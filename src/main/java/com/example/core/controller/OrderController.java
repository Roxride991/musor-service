package com.example.core.controller;

import com.example.core.dto.*;
import com.example.core.model.*;
import com.example.core.service.OrderService;
import com.example.core.service.GeocodingService;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.repository.SubscriptionRepository;
import com.example.core.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DtoMapper dtoMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final GeocodingService geocodingService;
    private final UserRepository userRepository; // ДОБАВЛЕН

    public OrderController(
            OrderService orderService,
            DtoMapper dtoMapper,
            SubscriptionRepository subscriptionRepository,
            GeocodingService geocodingService,
            UserRepository userRepository // ДОБАВЛЕН
    ) {
        this.orderService = orderService;
        this.dtoMapper = dtoMapper;
        this.subscriptionRepository = subscriptionRepository;
        this.geocodingService = geocodingService;
        this.userRepository = userRepository; // ДОБАВЛЕН
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateOrderRequest request) {

        // Загружаем свежего пользователя из БД для гарантии правильной роли
        User freshUser = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        if (freshUser.getUserRole() != UserRole.CLIENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Subscription subscription = null;
            Long subId = request.getSubscriptionId();
            if (subId != null) {
                subscription = subscriptionRepository.findById(subId)
                        .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

                if (!subscription.getUser().getId().equals(freshUser.getId())) {
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

            // Используем freshUser вместо currentUser
            Order order = orderService.createOrder(
                    freshUser,
                    request.getAddress(),
                    request.getPickupTime(),
                    request.getComment(),
                    lat,
                    lng,
                    subscription
            );

            OrderResponse response = dtoMapper.toOrderResponse(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            OrderResponse errorResponse = OrderResponse.builder()
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (IllegalStateException e) {
            OrderResponse errorResponse = OrderResponse.builder()
                    .message("Ошибка сервера: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/address/suggestions")
    public ResponseEntity<List<AddressSuggestionResponse>> getAddressSuggestions(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        try {
            List<AddressSuggestionResponse> suggestions = geocodingService.suggestAddresses(query, limit).stream()
                    .map(item -> AddressSuggestionResponse.builder()
                            .address(item.address())
                            .lat(item.lat())
                            .lng(item.lng())
                            .build())
                    .toList();
            return ResponseEntity.ok(suggestions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(List.of());
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @AuthenticationPrincipal User currentUser) {

        try {
            List<Order> orders;
            if (currentUser.getUserRole() == UserRole.CLIENT) {
                orders = orderService.getOrdersForClient(currentUser);
            } else if (currentUser.getUserRole() == UserRole.COURIER) {
                var available = orderService.getAvailableOrdersForCourier(currentUser);
                var ownActive = orderService.getActiveOrdersForCourier(currentUser);
                orders = java.util.stream.Stream.concat(available.stream(), ownActive.stream())
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
            } else { // ADMIN
                orders = orderService.getAllOrders(currentUser);
            }

            List<OrderResponse> responses = dtoMapper.toOrderResponses(orders);

            return ResponseEntity.ok(responses);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/available")
    public ResponseEntity<List<OrderResponse>> getAvailableOrders(
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> orders = orderService.getAvailableOrdersForCourier(currentUser);
            List<OrderResponse> responses = dtoMapper.toOrderResponses(orders, true);
            return ResponseEntity.ok(responses);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> orders = orderService.getActiveOrdersForCourier(currentUser);
            List<OrderResponse> responses = dtoMapper.toOrderResponses(orders, false);
            return ResponseEntity.ok(responses);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<OrderStatsResponse> getOrderStats(
            @AuthenticationPrincipal User currentUser) {

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

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

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
            } else { // ADMIN
                order = orderService.getOrderById(id);
            }

            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            OrderResponse response = dtoMapper.toOrderResponse(order);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        if (currentUser.getUserRole() != UserRole.CLIENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            orderService.cancelByClient(id, currentUser);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<OrderResponse> acceptOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Order order = orderService.acceptOrder(id, currentUser);
            OrderResponse response = dtoMapper.toOrderResponse(order);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Order order = orderService.updateStatusByCourier(id, currentUser, request.getStatus());
            OrderResponse response = dtoMapper.toOrderResponse(order);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/admin/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatusByAdmin(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        if (currentUser.getUserRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Order order = orderService.updateStatusByAdmin(id, request.getStatus(), currentUser);
            OrderResponse response = dtoMapper.toOrderResponse(order);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
