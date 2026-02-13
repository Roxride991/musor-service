package com.example.core.service;

import com.example.core.model.*;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.ServiceZoneRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final LocalTime SLOT_1_START = LocalTime.of(8, 0);
    private static final LocalTime SLOT_1_END = LocalTime.of(11, 0);
    private static final LocalTime SLOT_2_START = LocalTime.of(13, 0);
    private static final LocalTime SLOT_2_END = LocalTime.of(16, 0);
    private static final LocalTime SLOT_3_START = LocalTime.of(19, 0);
    private static final LocalTime SLOT_3_END = LocalTime.of(21, 0);

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

        validatePickupTime(pickupTime);

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

    private void validatePickupTime(OffsetDateTime pickupTime) {
        if (pickupTime == null) {
            throw new IllegalArgumentException("Время вывоза обязательно");
        }

        Instant now = Instant.now();
        Instant pickupInstant = pickupTime.toInstant();

        if (pickupInstant.isBefore(now.plus(1, ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("Время вывоза должно быть не ранее чем через 1 час");
        }
        if (pickupInstant.isAfter(now.plus(7, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("Максимальный срок — 7 дней");
        }

        LocalTime localPickupTime = pickupTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
        if (!isAllowedTimeSlot(localPickupTime)) {
            throw new IllegalArgumentException(
                    "Доступные интервалы вывоза: 08:00-11:00, 13:00-16:00, 19:00-21:00"
            );
        }
    }

    private boolean isAllowedTimeSlot(LocalTime time) {
        return isInSlot(time, SLOT_1_START, SLOT_1_END)
                || isInSlot(time, SLOT_2_START, SLOT_2_END)
                || isInSlot(time, SLOT_3_START, SLOT_3_END);
    }

    private boolean isInSlot(LocalTime time, LocalTime start, LocalTime endExclusive) {
        return !time.isBefore(start) && time.isBefore(endExclusive);
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
        restoreSubscriptionUsageIfNeeded(order);
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
        if (newStatus == OrderStatus.CANCELLED_BY_COURIER) {
            restoreSubscriptionUsageIfNeeded(order);
        }
        return orderRepository.save(order);
    }

    private void restoreSubscriptionUsageIfNeeded(Order order) {
        Subscription subscription = order.getSubscription();
        if (subscription == null) {
            return;
        }

        if (subscription.getUsedOrders() > 0) {
            subscription.setUsedOrders(subscription.getUsedOrders() - 1);
            subscriptionRepository.save(subscription);
        }
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
