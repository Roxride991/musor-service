package com.example.core.service;

import com.example.core.dto.OrderAdminFilter;
import com.example.core.model.*;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.ServiceZoneRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final double DEFAULT_CLUSTER_RADIUS_METERS = 50.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final LocalTime SLOT_1_START = LocalTime.of(8, 0);
    private static final LocalTime SLOT_1_END = LocalTime.of(11, 0);
    private static final LocalTime SLOT_2_START = LocalTime.of(13, 0);
    private static final LocalTime SLOT_2_END = LocalTime.of(16, 0);
    private static final LocalTime SLOT_3_START = LocalTime.of(19, 0);
    private static final LocalTime SLOT_3_END = LocalTime.of(21, 0);
    private static final String SLOT_8_11 = "08:00-11:00";
    private static final String SLOT_13_16 = "13:00-16:00";
    private static final String SLOT_19_21 = "19:00-21:00";
    private static final Map<OrderStatus, List<OrderStatus>> ADMIN_ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PUBLISHED, List.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED_BY_CUSTOMER, OrderStatus.CANCELLED_BY_COURIER),
            OrderStatus.ACCEPTED, List.of(OrderStatus.ON_THE_WAY, OrderStatus.CANCELLED_BY_CUSTOMER, OrderStatus.CANCELLED_BY_COURIER),
            OrderStatus.ON_THE_WAY, List.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED_BY_CUSTOMER, OrderStatus.CANCELLED_BY_COURIER),
            OrderStatus.PICKED_UP, List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED_BY_CUSTOMER, OrderStatus.CANCELLED_BY_COURIER)
    );

    private final OrderRepository orderRepository;
    private final ServiceZoneRepository zoneRepository;
    private final GeoUtils geoUtils;
    private final PaymentService paymentService;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSchedulingService subscriptionSchedulingService;
    private final AuditService auditService;

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
            throw new IllegalArgumentException(
                    String.format(
                            "Этот адрес вне зоны обслуживания (%.6f, %.6f). Выберите другой адрес или точку на карте.",
                            lat,
                            lng
                    )
            );
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
                .lat(lat)
                .lng(lng)
                .subscription(subscription)
                .status(OrderStatus.PUBLISHED)
                .build();

        Order saved = orderRepository.save(order);

        if (subscription != null) {
            subscription.setUsedOrders(subscription.getUsedOrders() + 1);
            subscriptionRepository.save(subscription);
        }
        auditService.log(
                "ORDER_CREATE",
                "SUCCESS",
                client,
                "ORDER_ID",
                String.valueOf(saved.getId()),
                "Order created with status " + saved.getStatus(),
                null
        );

        return saved;
    }

    private void validatePickupTime(OffsetDateTime pickupTime) {
        if (pickupTime == null) {
            throw new IllegalArgumentException("Время вывоза обязательно");
        }

        Instant now = Instant.now();
        Instant pickupInstant = pickupTime.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate pickupDate = pickupTime.atZoneSameInstant(zoneId).toLocalDate();
        LocalDate today = Instant.now().atZone(zoneId).toLocalDate();

        if (pickupInstant.isBefore(now.plus(1, ChronoUnit.HOURS))) {
            if (pickupDate.equals(today)) {
                throw new IllegalArgumentException(
                        "Выбранный интервал на сегодня уже недоступен. Выберите завтрашнюю дату или более поздний слот."
                );
            }
            throw new IllegalArgumentException("Время вывоза должно быть не ранее чем через 1 час");
        }
        if (pickupInstant.isAfter(now.plus(7, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("Максимальный срок — 7 дней");
        }

        LocalTime localPickupTime = pickupTime.atZoneSameInstant(zoneId).toLocalTime();
        if (!isAllowedTimeSlot(localPickupTime)) {
            throw new IllegalArgumentException(
                    "Выбранное время не входит в доступные слоты. Доступные интервалы: 08:00-11:00, 13:00-16:00, 19:00-21:00"
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
        if (lat == null || lng == null) {
            throw new IllegalArgumentException(
                    "Координаты адреса не определены. Выберите адрес из подсказки или отметьте точку на карте."
            );
        }

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

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED_BY_CUSTOMER);
        restoreSubscriptionUsageIfNeeded(order);
        Order saved = orderRepository.save(order);
        scheduleNextSubscriptionOrderIfNeeded(saved);
        auditService.log(
                "ORDER_STATUS_CHANGE",
                "SUCCESS",
                client,
                "ORDER_ID",
                String.valueOf(saved.getId()),
                "Client status change: " + previousStatus + " -> " + saved.getStatus(),
                null
        );
        return saved;
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

    public List<Order> getOrdersForClustering(User actor, OrderStatus status, boolean onlyUnassigned, int limit) {
        if (actor == null || actor.getUserRole() == null) {
            throw new IllegalStateException("Пользователь не аутентифицирован");
        }
        if (actor.getUserRole() != UserRole.ADMIN && actor.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Недостаточно прав для просмотра кластеров");
        }

        OrderStatus safeStatus = status == null ? OrderStatus.PUBLISHED : status;
        int safeLimit = Math.max(1, Math.min(limit, 1000));

        List<Order> source = onlyUnassigned
                ? orderRepository.findByStatusAndCourierIsNull(safeStatus)
                : orderRepository.findByStatus(safeStatus);

        return source.stream()
                .sorted(java.util.Comparator.comparing(
                        Order::getPickupTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
                ))
                .limit(safeLimit)
                .toList();
    }

    @Transactional
    public List<Order> acceptClusterByCourier(User courier, List<Long> orderIds, Double requestedRadiusMeters) {
        if (courier == null || courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьеры могут принимать кластер заказов");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("Список заказов кластера пуст");
        }

        double radiusMeters = normalizeClusterRadius(requestedRadiusMeters);
        List<Long> uniqueIds = orderIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("Список заказов кластера пуст");
        }

        List<Order> orders = new java.util.ArrayList<>();
        for (Long orderId : uniqueIds) {
            Order order = orderRepository.findByIdWithLock(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));
            if (order.getStatus() != OrderStatus.PUBLISHED || order.getCourier() != null) {
                throw new IllegalStateException("Заказ уже недоступен для принятия: " + orderId);
            }
            orders.add(order);
        }

        ensureSamePickupDateAndSlot(orders);
        ensureSingleSpatialCluster(orders, radiusMeters);

        List<Order> savedOrders = new java.util.ArrayList<>();
        for (Order order : orders) {
            order.setCourier(courier);
            order.setStatus(OrderStatus.ACCEPTED);
            Order saved = orderRepository.save(order);
            savedOrders.add(saved);
            auditService.log(
                    "ORDER_STATUS_CHANGE",
                    "SUCCESS",
                    courier,
                    "ORDER_ID",
                    String.valueOf(saved.getId()),
                    "Courier accepted via cluster: PUBLISHED -> ACCEPTED",
                    null
            );
        }

        return savedOrders.stream()
                .sorted(java.util.Comparator.comparing(
                        Order::getPickupTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
                ))
                .toList();
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

        OrderStatus previousStatus = order.getStatus();
        order.setCourier(courier);
        order.setStatus(OrderStatus.ACCEPTED);
        Order saved = orderRepository.save(order);
        auditService.log(
                "ORDER_STATUS_CHANGE",
                "SUCCESS",
                courier,
                "ORDER_ID",
                String.valueOf(saved.getId()),
                "Courier accepted order: " + previousStatus + " -> " + saved.getStatus(),
                null
        );
        return saved;
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
        Order saved = orderRepository.save(order);
        auditService.log(
                "ORDER_STATUS_CHANGE",
                "SUCCESS",
                courier,
                "ORDER_ID",
                String.valueOf(saved.getId()),
                "Courier status change: " + currentStatus + " -> " + saved.getStatus(),
                null
        );

        if (newStatus == OrderStatus.CANCELLED_BY_COURIER || newStatus == OrderStatus.COMPLETED) {
            scheduleNextSubscriptionOrderIfNeeded(saved);
        }
        return saved;
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

    public List<Order> getFilteredOrdersForAdmin(User admin, OrderAdminFilter filter, int limit) {
        if (admin.getUserRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Только администраторы могут фильтровать заказы");
        }

        OrderAdminFilter safeFilter = filter == null ? OrderAdminFilter.builder().build() : filter;
        int safeLimit = Math.max(1, Math.min(limit, 500));
        Specification<Order> specification = buildAdminSpecification(safeFilter);
        return orderRepository.findAll(
                specification,
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "pickupTime"))
        ).getContent();
    }

    @Transactional
    public Order updateStatusByAdmin(Long orderId, OrderStatus newStatus, User admin) {
        if (admin.getUserRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Только администраторы могут менять статусы");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        OrderStatus currentStatus = order.getStatus();
        if (newStatus == currentStatus) {
            return order;
        }
        if (!isAllowedAdminTransition(currentStatus, newStatus)) {
            throw new IllegalStateException("Недопустимый переход статуса: " + currentStatus + " -> " + newStatus);
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.CANCELLED_BY_CUSTOMER || newStatus == OrderStatus.CANCELLED_BY_COURIER) {
            restoreSubscriptionUsageIfNeeded(order);
        }

        Order saved = orderRepository.save(order);
        auditService.log(
                "ORDER_STATUS_CHANGE",
                "SUCCESS",
                admin,
                "ORDER_ID",
                String.valueOf(saved.getId()),
                "Admin status change: " + currentStatus + " -> " + saved.getStatus(),
                null
        );
        if (newStatus == OrderStatus.COMPLETED ||
                newStatus == OrderStatus.CANCELLED_BY_CUSTOMER ||
                newStatus == OrderStatus.CANCELLED_BY_COURIER) {
            scheduleNextSubscriptionOrderIfNeeded(saved);
        }
        return saved;
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
    }

    public Order getOrderByIdAndCourier(Long id, User courier) {
        return orderRepository.findByIdAndCourier(id, courier)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден или не ваш"));
    }

    private void scheduleNextSubscriptionOrderIfNeeded(Order order) {
        Subscription subscription = order.getSubscription();
        if (subscription == null || subscription.getId() == null) {
            return;
        }

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }

        try {
            subscriptionSchedulingService.scheduleNextOrderIfNeeded(subscription.getId());
        } catch (IllegalArgumentException e) {
            // Subscription may be deleted/invalid; no-op for order status flow
        }
    }

    private boolean isAllowedAdminTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        List<OrderStatus> allowed = ADMIN_ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    private Specification<Order> buildAdminSpecification(OrderAdminFilter filter) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatuses()));
            }
            if (filter.getPickupFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("pickupTime"), filter.getPickupFrom()));
            }
            if (filter.getPickupTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("pickupTime"), filter.getPickupTo()));
            }
            if (filter.getClientId() != null) {
                predicates.add(cb.equal(root.get("client").get("id"), filter.getClientId()));
            }
            if (filter.getCourierId() != null) {
                predicates.add(cb.equal(root.get("courier").get("id"), filter.getCourierId()));
            }
            if (Boolean.TRUE.equals(filter.getOnlyUnassigned())) {
                predicates.add(cb.isNull(root.get("courier")));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void ensureSamePickupDateAndSlot(List<Order> orders) {
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Кластер не содержит заказов");
        }

        OffsetDateTime firstPickup = orders.get(0).getPickupTime();
        if (firstPickup == null) {
            throw new IllegalArgumentException("Заказ не содержит времени вывоза");
        }
        LocalDate expectedDate = firstPickup.toLocalDate();
        String expectedSlot = resolveSlotLabel(firstPickup.toLocalTime());

        for (Order order : orders) {
            OffsetDateTime pickupTime = order.getPickupTime();
            if (pickupTime == null) {
                throw new IllegalArgumentException("Заказ не содержит времени вывоза: " + order.getId());
            }
            LocalDate date = pickupTime.toLocalDate();
            if (!expectedDate.equals(date)) {
                throw new IllegalStateException("В кластере должна быть одинаковая дата вывоза");
            }

            String slot = resolveSlotLabel(pickupTime.toLocalTime());
            if (!expectedSlot.equals(slot)) {
                throw new IllegalStateException("В кластере должен быть одинаковый временной слот вывоза");
            }
        }
    }

    private void ensureSingleSpatialCluster(List<Order> orders, double radiusMeters) {
        List<OrderCoordinate> coordinates = orders.stream()
                .map(this::resolveOrderCoordinate)
                .toList();
        int n = coordinates.size();
        if (n <= 1) {
            return;
        }

        boolean[][] adjacency = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            adjacency[i][i] = true;
            for (int j = i + 1; j < n; j++) {
                double distance = haversineMeters(
                        coordinates.get(i).lat,
                        coordinates.get(i).lng,
                        coordinates.get(j).lat,
                        coordinates.get(j).lng
                );
                if (distance <= radiusMeters) {
                    adjacency[i][j] = true;
                    adjacency[j][i] = true;
                }
            }
        }

        java.util.Set<Integer> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        visited.add(0);
        queue.add(0);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next = 0; next < n; next++) {
                if (!adjacency[current][next] || visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }

        if (visited.size() != n) {
            throw new IllegalStateException("Заказы не образуют один кластер в радиусе " + (int) radiusMeters + " м");
        }
    }

    private OrderCoordinate resolveOrderCoordinate(Order order) {
        Double lat = order.getLat();
        Double lng = order.getLng();
        if ((lat == null || lng == null) && order.getSubscription() != null) {
            lat = lat == null ? order.getSubscription().getServiceLat() : lat;
            lng = lng == null ? order.getSubscription().getServiceLng() : lng;
        }
        if (lat == null || lng == null) {
            throw new IllegalStateException("У заказа нет координат: " + order.getId());
        }
        return new OrderCoordinate(lat, lng);
    }

    private double normalizeClusterRadius(Double requestedRadiusMeters) {
        if (requestedRadiusMeters == null || requestedRadiusMeters.isNaN() || requestedRadiusMeters.isInfinite()) {
            return DEFAULT_CLUSTER_RADIUS_METERS;
        }
        return Math.max(10.0, Math.min(500.0, requestedRadiusMeters));
    }

    private String resolveSlotLabel(LocalTime time) {
        if (time == null) {
            return "CUSTOM";
        }
        if (isInSlot(time, SLOT_1_START, SLOT_1_END)) {
            return SLOT_8_11;
        }
        if (isInSlot(time, SLOT_2_START, SLOT_2_END)) {
            return SLOT_13_16;
        }
        if (isInSlot(time, SLOT_3_START, SLOT_3_END)) {
            return SLOT_19_21;
        }
        return "CUSTOM";
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private record OrderCoordinate(
            double lat,
            double lng
    ) {
    }
}
