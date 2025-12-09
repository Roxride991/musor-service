package com.example.core.service;

import com.example.core.model.*;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.ServiceZoneRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ServiceZoneRepository zoneRepository;
    private final GeoUtils geoUtils;
    private final PaymentService paymentService;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Order createOrder(User client, String address, OffsetDateTime pickupTime,
                             String comment, Double lat, Double lng, Subscription subscription) {

        // ДОБАВЛЕНА ПРОВЕРКА НА NULL
        if (client == null) {
            throw new IllegalStateException("Пользователь не может быть null");
        }

        if (client.getUserRole() == null) {
            throw new IllegalStateException("Роль пользователя не установлена");
        }

        if (client.getUserRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Только клиенты могут создавать заказы");
        }

        if (!isAddressInServiceZone(lat, lng)) {
            throw new IllegalArgumentException("Адрес вне зоны обслуживания");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (pickupTime.isBefore(now.plusHours(1))) {
            throw new IllegalArgumentException("Время вывоза должно быть не ранее чем через 1 час");
        }
        if (pickupTime.isAfter(now.plusDays(7))) {
            throw new IllegalArgumentException("Максимальный срок — 7 дней");
        }

        if (subscription != null) {
            if (!subscription.hasAvailableOrders()) {
                throw new IllegalArgumentException("Лимит вывозов по подписке исчерпан");
            }
            if (subscription.getEndDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Срок подписки истёк");
            }
        }

        Order order = Order.builder()
                .client(client)
                .address(address)
                .pickupTime(pickupTime)
                .comment(comment)
                .subscription(subscription)
                .status(OrderStatus.PUBLISHED)
                .build();

        Order saved = orderRepository.save(order);

        if (subscription != null) {
            subscription.setUsedOrders(subscription.getUsedOrders() + 1);
            subscriptionRepository.save(subscription);
        }

        return saved;
    }

    private boolean isAddressInServiceZone(Double lat, Double lng) {
        ServiceZone activeZone = zoneRepository.findFirstByActiveTrue()
                .orElseThrow(() -> new IllegalArgumentException("Активная зона обслуживания не настроена. Обратитесь к администратору."));

        if (activeZone.getCoordinates() == null || activeZone.getCoordinates().isEmpty()) {
            throw new IllegalArgumentException("Активная зона не содержит координат");
        }

        return geoUtils.isPointInPolygon(lat, lng, activeZone.getCoordinates());
    }

    // Остальные методы остаются без изменений
    public List<Order> getOrdersForClient(User client) {
        if (client.getUserRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Только клиенты могут просматривать свои заказы");
        }
        return orderRepository.findByClient(client);
    }

    @Transactional
    public Order cancelByClient(Long orderId, User client) {
        if (client.getUserRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Только клиенты могут отменять заказы");
        }

        Order order = orderRepository.findByIdAndClient(orderId, client)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден или недоступен"));

        if (order.getStatus() == OrderStatus.COMPLETED ||
                order.getStatus() == OrderStatus.CANCELLED_BY_COURIER ||
                order.getStatus() == OrderStatus.ON_THE_WAY ||
                order.getStatus() == OrderStatus.PICKED_UP) {
            throw new IllegalStateException("Нельзя отменить заказ на текущем этапе");
        }

        order.setStatus(OrderStatus.CANCELLED_BY_CUSTOMER);
        return orderRepository.save(order);
    }

    public List<Order> getAvailableOrdersForCourier(User courier) {
        if (courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьеры могут просматривать доступные заказы");
        }
        return orderRepository.findByStatusAndCourierIsNull(OrderStatus.PUBLISHED);
    }

    public List<Order> getActiveOrdersForCourier(User courier) {
        if (courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьеры могут просматривать свои заказы");
        }
        List<Order> all = orderRepository.findByCourier(courier);
        return all.stream()
                .filter(o -> o.getStatus() == OrderStatus.ACCEPTED
                        || o.getStatus() == OrderStatus.ON_THE_WAY
                        || o.getStatus() == OrderStatus.PICKED_UP)
                .toList();
    }

    public java.util.Map<String, Long> getOrderStatsForCourier(User courier) {
        if (courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьеры могут просматривать статистику заказов");
        }

        long availableCount = getAvailableOrdersForCourier(courier).size();
        long activeCount = getActiveOrdersForCourier(courier).size();

        return java.util.Map.of(
                "availableCount", availableCount,
                "activeCount", activeCount
        );
    }

    @Transactional
    public Order acceptOrder(Long orderId, User courier) {
        if (courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьеры могут брать заказы");
        }

        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        if (order.getStatus() != OrderStatus.PUBLISHED) {
            throw new IllegalStateException("Заказ уже принят другим курьером");
        }
        if (order.getCourier() != null) {
            throw new IllegalStateException("Заказ уже назначен курьеру");
        }

        order.setCourier(courier);
        order.setStatus(OrderStatus.ACCEPTED);
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateStatusByCourier(Long orderId, User courier, OrderStatus newStatus) {
        if (courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьеры могут обновлять статусы");
        }

        Order order = orderRepository.findByIdAndCourier(orderId, courier)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден или не ваш"));

        OrderStatus currentStatus = order.getStatus();

        if (newStatus == OrderStatus.CANCELLED_BY_COURIER) {
            if (currentStatus != OrderStatus.ACCEPTED && currentStatus != OrderStatus.ON_THE_WAY) {
                throw new IllegalStateException("Можно отменить только заказ со статусом ACCEPTED или ON_THE_WAY");
            }
        } else if (newStatus == OrderStatus.ON_THE_WAY) {
            if (currentStatus != OrderStatus.ACCEPTED) {
                throw new IllegalStateException("Можно установить ON_THE_WAY только из ACCEPTED");
            }
        } else if (newStatus == OrderStatus.PICKED_UP) {
            if (currentStatus != OrderStatus.ON_THE_WAY) {
                throw new IllegalStateException("Можно установить PICKED_UP только из ON_THE_WAY");
            }
        } else if (newStatus == OrderStatus.COMPLETED) {
            if (currentStatus != OrderStatus.PICKED_UP) {
                throw new IllegalStateException("Можно завершить только заказ со статусом PICKED_UP");
            }
        } else {
            throw new IllegalStateException("Курьер не может установить статус " + newStatus);
        }

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public List<Order> getAllOrders(User admin) {
        if (admin.getUserRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Только администраторы могут просматривать все заказы");
        }
        return orderRepository.findAll();
    }

    @Transactional
    public Order updateStatusByAdmin(Long orderId, OrderStatus newStatus, User admin) {
        if (admin.getUserRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Только администраторы могут менять статусы");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
    }

    public Order getOrderByIdAndCourier(Long id, User courier) {
        return orderRepository.findByIdAndCourier(id, courier)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден или не ваш"));
    }
}