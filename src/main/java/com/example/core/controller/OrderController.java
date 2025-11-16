package com.example.core.controller;

import com.example.core.dto.*;
import com.example.core.model.*;
import com.example.core.service.OrderService;
import com.example.core.service.GeocodingService;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.repository.SubscriptionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DtoMapper dtoMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final GeocodingService geocodingService;

    public OrderController(
            OrderService orderService,
            DtoMapper dtoMapper,
            SubscriptionRepository subscriptionRepository,
            GeocodingService geocodingService
    ) {
        this.orderService = orderService;
        this.dtoMapper = dtoMapper;
        this.subscriptionRepository = subscriptionRepository;
        this.geocodingService = geocodingService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateOrderRequest request) {

        if (currentUser.getUserRole() != UserRole.CLIENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Subscription subscription = null;
            Long subId = request.getSubscriptionId();
            if (subId != null) {
                subscription = subscriptionRepository.findById(subId)
                        .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

                if (!subscription.getUser().getId().equals(currentUser.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                    throw new IllegalArgumentException("Подписка не активна");
                }
            }

            // ✅ ГЕОКОДИНГ ДО ТРАНЗАКЦИИ
            Double lat = request.getLat();
            Double lng = request.getLng();

            if (lat == null || lng == null) {
                ServiceZone.Coordinate coord = geocodingService.getCoordinates(request.getAddress());
                lat = coord.getLat();
                lng = coord.getLng();
            }


            Order order = orderService.createOrder(
                    currentUser,
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
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @AuthenticationPrincipal User currentUser) {

        try {
            List<Order> orders;
            if (currentUser.getUserRole() == UserRole.CLIENT) {
                orders = orderService.getOrdersForClient(currentUser);
            } else if (currentUser.getUserRole() == UserRole.COURIER) {
                // Для курьера возвращаем объединенный список (для обратной совместимости)
                // Но рекомендуется использовать /available и /active
                var available = orderService.getAvailableOrdersForCourier(currentUser);
                var ownActive = orderService.getActiveOrdersForCourier(currentUser);
                orders = java.util.stream.Stream.concat(available.stream(), ownActive.stream())
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
            } else { // ADMIN
                orders = orderService.getAllOrders(currentUser);
            }

            List<OrderResponse> responses = orders.stream()
                    .map(dtoMapper::toOrderResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Получение свободных заказов для курьера (статус PUBLISHED, не назначены курьеру).
     */
    @GetMapping("/available")
    public ResponseEntity<List<OrderResponse>> getAvailableOrders(
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> orders = orderService.getAvailableOrdersForCourier(currentUser);
            List<OrderResponse> responses = orders.stream()
                    .map(order -> dtoMapper.toOrderResponse(order, true)) // isAvailable = true
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Получение активных заказов курьера (ACCEPTED, ON_THE_WAY, PICKED_UP).
     */
    @GetMapping("/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders(
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getUserRole() != UserRole.COURIER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Order> orders = orderService.getActiveOrdersForCourier(currentUser);
            List<OrderResponse> responses = orders.stream()
                    .map(order -> dtoMapper.toOrderResponse(order, false)) // isAvailable = false
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Получение статистики заказов для курьера (количество свободных и активных заказов).
     */
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

    /** Получение деталей заказа по ID с учётом роли и доступа. */
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
                // Доступны: свои заказы + свободные
                order = orderService.getAvailableOrdersForCourier(currentUser).stream()
                        .filter(o -> o.getId().equals(id))
                        .findFirst()
                        .orElse(null);
                if (order == null) {
                    // Проверим, принадлежит ли заказ курьеру
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
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Отмена заказа клиентом до взятия/выполнения. */
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

    /** Принятие заказа курьером (только COURIER). */
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

    /** Обновление статуса заказа курьером. */
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

    /** Ручное обновление статуса администратором. */
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