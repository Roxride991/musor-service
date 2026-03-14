package com.example.core.controller;

import com.example.core.dto.PaymentCheckoutResponse;
import com.example.core.dto.PaymentInitRequest;
import com.example.core.dto.PaymentResponse;
import com.example.core.dto.PaymentWebhookRequest;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.model.Order;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.SubscriptionRepository;
import com.example.core.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DtoMapper dtoMapper;

    @PostMapping("/orders/{orderId}/init")
    public ResponseEntity<?> initOrderPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @RequestBody(required = false) PaymentInitRequest request
    ) {
        try {
            PaymentInitRequest safeRequest = request == null ? new PaymentInitRequest() : request;
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

            if (!canManageOrderPayment(currentUser, order)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Нет доступа к оплате заказа"));
            }

            // Important: payment amount is always calculated on server side.
            java.math.BigDecimal serverAmount = paymentService.resolveOrderAmount(order);
            PaymentCheckoutResponse response = paymentService.initOrderPayment(
                    currentUser,
                    order,
                    serverAmount,
                    safeRequest.getReturnUrl(),
                    safeRequest.getDescription()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/subscriptions/{subscriptionId}/init")
    public ResponseEntity<?> initSubscriptionPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long subscriptionId,
            @RequestBody(required = false) PaymentInitRequest request
    ) {
        try {
            PaymentInitRequest safeRequest = request == null ? new PaymentInitRequest() : request;
            Subscription subscription = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

            if (!canManageSubscriptionPayment(currentUser, subscription)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Нет доступа к оплате подписки"));
            }

            // Important: payment amount is always calculated on server side.
            java.math.BigDecimal serverAmount = paymentService.resolveSubscriptionAmount(subscription);
            PaymentCheckoutResponse response = paymentService.initSubscriptionPayment(
                    currentUser,
                    subscription,
                    serverAmount,
                    safeRequest.getReturnUrl(),
                    safeRequest.getDescription()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{paymentId}/sync")
    public ResponseEntity<?> syncPaymentStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long paymentId
    ) {
        try {
            return ResponseEntity.ok(dtoMapper.toPaymentResponse(paymentService.synchronizePaymentStatus(paymentId, currentUser)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                paymentService.getPaymentsForUser(currentUser).stream()
                        .map(dtoMapper::toPaymentResponse)
                        .toList()
        );
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long paymentId
    ) {
        try {
            return ResponseEntity.ok(dtoMapper.toPaymentResponse(paymentService.getPaymentForUser(paymentId, currentUser)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/webhook/yookassa")
    public ResponseEntity<?> yookassaWebhook(
            @RequestHeader(value = "X-Webhook-Token", required = false) String webhookToken,
            @RequestBody PaymentWebhookRequest request
    ) {
        try {
            return ResponseEntity.ok(dtoMapper.toPaymentResponse(paymentService.handleWebhook(request, webhookToken)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    private boolean canManageOrderPayment(User currentUser, Order order) {
        if (currentUser.getUserRole() == UserRole.ADMIN) {
            return true;
        }
        if (order.getClient() == null || order.getClient().getId() == null) {
            return false;
        }
        return order.getClient().getId().equals(currentUser.getId());
    }

    private boolean canManageSubscriptionPayment(User currentUser, Subscription subscription) {
        if (currentUser.getUserRole() == UserRole.ADMIN) {
            return true;
        }
        if (subscription.getUser() == null || subscription.getUser().getId() == null) {
            return false;
        }
        return subscription.getUser().getId().equals(currentUser.getId());
    }
}
