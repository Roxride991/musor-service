package com.example.core.controller;

import com.example.core.dto.AddressSuggestionResponse;
import com.example.core.dto.AcceptOrderClusterRequest;
import com.example.core.dto.AuditEventResponse;
import com.example.core.dto.CreateOrderRequest;
import com.example.core.dto.DispatchRecommendationResponse;
import com.example.core.dto.OrderAdminFilter;
import com.example.core.dto.OrderClusteringResponse;
import com.example.core.dto.OrderResponse;
import com.example.core.dto.OrderStatsResponse;
import com.example.core.dto.UpdateOrderStatusRequest;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.NotificationType;
import com.example.core.model.ServiceZone;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.monitoring.FlowMetricsService;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.SubscriptionRepository;
import com.example.core.repository.UserRepository;
import com.example.core.service.AuditService;
import com.example.core.service.GeocodingService;
import com.example.core.service.NotificationService;
import com.example.core.service.OperatorDashboardService;
import com.example.core.service.OrderClusteringService;
import com.example.core.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DtoMapper dtoMapper;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GeocodingService geocodingService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final OperatorDashboardService operatorDashboardService;
    private final FlowMetricsService flowMetricsService;
    private final NotificationService notificationService;
    private final OrderClusteringService orderClusteringService;

    public OrderController(
            OrderService orderService,
            DtoMapper dtoMapper,
            OrderRepository orderRepository,
            SubscriptionRepository subscriptionRepository,
            GeocodingService geocodingService,
            UserRepository userRepository,
            AuditService auditService,
            OperatorDashboardService operatorDashboardService,
            FlowMetricsService flowMetricsService,
            NotificationService notificationService,
            OrderClusteringService orderClusteringService
    ) {
        this.orderService = orderService;
        this.dtoMapper = dtoMapper;
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.geocodingService = geocodingService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.operatorDashboardService = operatorDashboardService;
        this.flowMetricsService = flowMetricsService;
        this.notificationService = notificationService;
        this.orderClusteringService = orderClusteringService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        User freshUser = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        if (freshUser.getUserRole() != UserRole.CLIENT) {
            flowMetricsService.recordOrderCreateFailure();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Subscription subscription = null;
            Long subId = request.getSubscriptionId();
            if (subId != null) {
                subscription = subscriptionRepository.findById(subId)
                        .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

                if (!subscription.getUser().getId().equals(freshUser.getId())) {
                    flowMetricsService.recordOrderCreateFailure();
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                    throw new IllegalArgumentException("Подписка не активна");
                }
            }

            Double lat = request.getLat();
            Double lng = request.getLng();

            if (lat == null || lng == null) {
                try {
                    ServiceZone.Coordinate coord = geocodingService.getCoordinates(request.getAddress());
                    lat = coord.getLat();
                    lng = coord.getLng();
                } catch (IllegalStateException geocodingError) {
                    throw new IllegalArgumentException(
                            "Не удалось определить координаты по адресу. Выберите адрес из подсказки или отметьте точку на карте."
                    );
                }
            }

            Order order = orderService.createOrder(
                    freshUser,
                    request.getAddress(),
                    request.getPickupTime(),
                    request.getComment(),
                    lat,
                    lng,
                    subscription
            );

            flowMetricsService.recordOrderCreateSuccess();
            notificationService.enqueueInApp(
                    freshUser,
                    NotificationType.ORDER_STATUS,
                    "Заказ создан",
                    "Заказ №" + order.getId() + " успешно создан",
                    order.getId(),
                    order.getSubscription() == null ? null : order.getSubscription().getId(),
                    "order-created-" + order.getId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toOrderResponse(order));

        } catch (IllegalArgumentException e) {
            flowMetricsService.recordOrderCreateFailure();
            return ResponseEntity.badRequest().body(OrderResponse.builder().message(e.getMessage()).build());
        } catch (IllegalStateException e) {
            flowMetricsService.recordOrderCreateFailure();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OrderResponse.builder().message("Ошибка сервера: " + e.getMessage()).build());
        }
    }

    @GetMapping("/address/suggestions")
    public ResponseEntity<List<AddressSuggestionResponse>> getAddressSuggestions(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        int safeLimit = normalizeSuggestionLimit(limit);
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 3) {
            return ResponseEntity.ok(List.of());
        }

        try {
            List<AddressSuggestionResponse> liveSuggestions = geocodingService.suggestAddresses(cleanQuery, safeLimit).stream()
                    .map(item -> AddressSuggestionResponse.builder()
                            .address(item.address())
                            .lat(item.lat())
                            .lng(item.lng())
                            .build())
                    .toList();

            List<AddressSuggestionResponse> historySuggestions = orderRepository
                    .findAddressHistorySuggestions(cleanQuery, safeLimit).stream()
                    .map(address -> AddressSuggestionResponse.builder()
                            .address(address)
                            .build())
                    .toList();

            return ResponseEntity.ok(mergeAddressSuggestions(liveSuggestions, historySuggestions, safeLimit));
        } catch (IllegalArgumentException e) {
            // Если геокодер не смог найти варианты по части строки, используем историю адресов.
            List<AddressSuggestionResponse> fallback = orderRepository
                    .findAddressHistorySuggestions(cleanQuery, safeLimit).stream()
                    .map(address -> AddressSuggestionResponse.builder()
                            .address(address)
                            .build())
                    .toList();
            return ResponseEntity.ok(fallback);
        } catch (IllegalStateException e) {
            // Fallback на локальную историю адресов, если геокодер временно недоступен.
            List<AddressSuggestionResponse> fallback = orderRepository
                    .findAddressHistorySuggestions(cleanQuery, safeLimit).stream()
                    .map(address -> AddressSuggestionResponse.builder()
                            .address(address)
                            .build())
                    .toList();
            return ResponseEntity.ok(fallback);
        }
    }

    private int normalizeSuggestionLimit(int limit) {
        return Math.max(1, Math.min(limit, 10));
    }

    private List<AddressSuggestionResponse> mergeAddressSuggestions(
            List<AddressSuggestionResponse> liveSuggestions,
            List<AddressSuggestionResponse> historySuggestions,
            int limit
    ) {
        LinkedHashMap<String, AddressSuggestionResponse> unique = new LinkedHashMap<>();
        appendSuggestions(unique, liveSuggestions);
        appendSuggestions(unique, historySuggestions);
        return unique.values().stream().limit(limit).toList();
    }

    private void appendSuggestions(
            Map<String, AddressSuggestionResponse> unique,
            List<AddressSuggestionResponse> suggestions
    ) {
        for (AddressSuggestionResponse suggestion : suggestions) {
            if (suggestion == null || suggestion.getAddress() == null) {
                continue;
            }
            String key = suggestion.getAddress().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }

            AddressSuggestionResponse existing = unique.get(key);
            if (existing == null || shouldReplaceWithGeo(existing, suggestion)) {
                unique.put(key, suggestion);
            }
        }
    }

    private boolean shouldReplaceWithGeo(AddressSuggestionResponse current, AddressSuggestionResponse candidate) {
        return (current.getLat() == null || current.getLng() == null)
                && candidate.getLat() != null
                && candidate.getLng() != null;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(@AuthenticationPrincipal User currentUser) {
        try {
            List<Order> orders;
            if (currentUser.getUserRole() == UserRole.CLIENT) {
                orders = orderService.getOrdersForClient(currentUser);
            } else if (currentUser.getUserRole() == UserRole.COURIER) {
                var available = orderService.getAvailableOrdersForCourier(currentUser);
                var ownActive = orderService.getActiveOrdersForCourier(currentUser);
                orders = java.util.stream.Stream.concat(available.stream(), ownActive.stream())
                        .distinct()
                        .toList();
            } else {
                orders = orderService.getAllOrders(currentUser);
            }

            return ResponseEntity.ok(dtoMapper.toOrderResponses(orders));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/available")
    public ResponseEntity<List<OrderResponse>> getAvailableOrders(@AuthenticationPrincipal User currentUser) {
        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> orders = orderService.getAvailableOrdersForCourier(currentUser);
            return ResponseEntity.ok(dtoMapper.toOrderResponses(orders, true));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(@AuthenticationPrincipal User currentUser) {
        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> orders = orderService.getActiveOrdersForCourier(currentUser);
            return ResponseEntity.ok(dtoMapper.toOrderResponses(orders, false));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<OrderStatsResponse> getOrderStats(@AuthenticationPrincipal User currentUser) {
        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            var stats = orderService.getOrderStatsForCourier(currentUser);
            OrderStatsResponse response = OrderStatsResponse.builder()
                    .availableCount(stats.get("availableCount"))
                    .activeCount(stats.get("activeCount"))
                    .build();
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/clusters")
    public ResponseEntity<?> getOrderClusters(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(name = "status", defaultValue = "PUBLISHED") OrderStatus status,
            @RequestParam(name = "onlyUnassigned", defaultValue = "true") boolean onlyUnassigned,
            @RequestParam(name = "radiusMeters", defaultValue = "50") double radiusMeters,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        try {
            List<Order> orders = orderService.getOrdersForClustering(currentUser, status, onlyUnassigned, limit);
            OrderClusteringResponse response = orderClusteringService.clusterOrders(orders, radiusMeters);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/clusters/accept")
    public ResponseEntity<?> acceptOrderCluster(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AcceptOrderClusterRequest request
    ) {
        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> accepted = orderService.acceptClusterByCourier(
                    currentUser,
                    request.getOrderIds(),
                    request.getRadiusMeters()
            );
            return ResponseEntity.ok(dtoMapper.toOrderResponses(accepted, false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/admin/filter")
    public ResponseEntity<?> filterOrdersForAdmin(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(name = "statuses", required = false) List<OrderStatus> statuses,
            @RequestParam(name = "pickupFrom", required = false) OffsetDateTime pickupFrom,
            @RequestParam(name = "pickupTo", required = false) OffsetDateTime pickupTo,
            @RequestParam(name = "clientId", required = false) Long clientId,
            @RequestParam(name = "courierId", required = false) Long courierId,
            @RequestParam(name = "onlyUnassigned", required = false) Boolean onlyUnassigned,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        if (currentUser.getUserRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            OrderAdminFilter filter = OrderAdminFilter.builder()
                    .statuses(statuses)
                    .pickupFrom(pickupFrom)
                    .pickupTo(pickupTo)
                    .clientId(clientId)
                    .courierId(courierId)
                    .onlyUnassigned(onlyUnassigned)
                    .build();
            List<Order> orders = orderService.getFilteredOrdersForAdmin(currentUser, filter, limit);
            return ResponseEntity.ok(dtoMapper.toOrderResponses(orders));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<?> operatorDashboard(@AuthenticationPrincipal User currentUser) {
        if (currentUser.getUserRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(operatorDashboardService.buildDashboard());
    }

    @GetMapping("/admin/dispatch/recommendations")
    public ResponseEntity<?> dispatchRecommendations(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(name = "limit", defaultValue = "25") int limit
    ) {
        if (currentUser.getUserRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<DispatchRecommendationResponse> recommendations = operatorDashboardService.buildDispatchRecommendations(limit);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        try {
            Order order;
            if (currentUser.getUserRole() == UserRole.CLIENT) {
                order = orderService.getOrdersForClient(currentUser).stream()
                        .filter(o -> o.getId().equals(id))
                        .findFirst()
                        .orElse(null);
            } else if (currentUser.getUserRole() == UserRole.COURIER) {
                order = orderService.getAvailableOrdersForCourier(currentUser).stream()
                        .filter(o -> o.getId().equals(id))
                        .findFirst()
                        .orElse(null);
                if (order == null) {
                    order = orderService.getOrderByIdAndCourier(id, currentUser);
                }
            } else {
                order = orderService.getOrderById(id);
            }

            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(dtoMapper.toOrderResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<?> getOrderTimeline(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        try {
            Order order = orderService.getOrderById(id);
            if (!canAccessOrderTimeline(currentUser, order)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            List<AuditEventResponse> timeline = auditService.getTimeline("ORDER_ID", String.valueOf(id), limit).stream()
                    .map(event -> AuditEventResponse.builder()
                            .id(event.getId())
                            .eventType(event.getEventType())
                            .outcome(event.getOutcome())
                            .actorUserId(event.getActorUserId())
                            .actorRole(event.getActorRole())
                            .targetType(event.getTargetType())
                            .targetId(event.getTargetId())
                            .clientIp(event.getClientIp())
                            .details(event.getDetails())
                            .createdAt(event.getCreatedAt())
                            .build())
                    .toList();

            return ResponseEntity.ok(timeline);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        if (currentUser.getUserRole() != UserRole.CLIENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            orderService.cancelByClient(id, currentUser);
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.ORDER_STATUS,
                    "Заказ отменен",
                    "Заказ №" + id + " отменен",
                    id,
                    null,
                    "order-cancel-client-" + id
            );
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<OrderResponse> acceptOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Order order = orderService.acceptOrder(id, currentUser);
            notificationService.enqueueInApp(
                    currentUser,
                    NotificationType.ORDER_STATUS,
                    "Заказ принят",
                    "Вы приняли заказ №" + id,
                    id,
                    null,
                    "order-accept-courier-" + id + "-" + currentUser.getId()
            );
            if (order.getClient() != null) {
                notificationService.enqueueInApp(
                        order.getClient(),
                        NotificationType.ORDER_STATUS,
                        "Назначен курьер",
                        "Курьер взял ваш заказ №" + id,
                        id,
                        null,
                        "order-accept-client-" + id
                );
            }
            return ResponseEntity.ok(dtoMapper.toOrderResponse(order));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Order order = orderService.updateStatusByCourier(id, currentUser, request.getStatus());
            if (order.getClient() != null) {
                notificationService.enqueueInApp(
                        order.getClient(),
                        NotificationType.ORDER_STATUS,
                        "Статус заказа обновлен",
                        "Заказ №" + id + " теперь в статусе " + order.getStatus(),
                        id,
                        null,
                        "order-status-client-" + id + "-" + order.getStatus()
                );
            }
            return ResponseEntity.ok(dtoMapper.toOrderResponse(order));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/admin/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatusByAdmin(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        if (currentUser.getUserRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Order order = orderService.updateStatusByAdmin(id, request.getStatus(), currentUser);
            if (order.getClient() != null) {
                notificationService.enqueueInApp(
                        order.getClient(),
                        NotificationType.ORDER_STATUS,
                        "Статус заказа обновлен администратором",
                        "Заказ №" + id + " теперь в статусе " + order.getStatus(),
                        id,
                        null,
                        "order-status-admin-client-" + id + "-" + order.getStatus()
                );
            }
            return ResponseEntity.ok(dtoMapper.toOrderResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean canAccessOrderTimeline(User user, Order order) {
        if (user == null || order == null || user.getUserRole() == null) {
            return false;
        }
        if (user.getUserRole() == UserRole.ADMIN) {
            return true;
        }
        if (user.getUserRole() == UserRole.CLIENT) {
            return order.getClient() != null
                    && order.getClient().getId() != null
                    && order.getClient().getId().equals(user.getId());
        }
        if (user.getUserRole() == UserRole.COURIER) {
            return order.getCourier() != null
                    && order.getCourier().getId() != null
                    && order.getCourier().getId().equals(user.getId());
        }
        return false;
    }
}
