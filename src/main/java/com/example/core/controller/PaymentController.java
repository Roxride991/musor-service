package com.example.core.controller;

import com.example.core.dto.PageResponse;
import com.example.core.dto.PaymentCheckoutResponse;
import com.example.core.dto.PaymentInitRequest;
import com.example.core.dto.PaymentResponse;
import com.example.core.dto.PaymentWebhookRequest;
import com.example.core.model.User;
import com.example.core.service.PaymentFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacadeService paymentFacadeService;

    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<PaymentCheckoutResponse> initOrderPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) PaymentInitRequest request
    ) {
        return ResponseEntity.ok(paymentFacadeService.initOrderPayment(currentUser, orderId, request));
    }

    @PostMapping("/subscriptions/{subscriptionId}/payments")
    public ResponseEntity<PaymentCheckoutResponse> initSubscriptionPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody(required = false) PaymentInitRequest request
    ) {
        return ResponseEntity.ok(paymentFacadeService.initSubscriptionPayment(currentUser, subscriptionId, request));
    }

    @PostMapping("/payments/{paymentId}/sync")
    public ResponseEntity<PaymentResponse> syncPaymentStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(paymentFacadeService.syncPaymentStatus(currentUser, paymentId));
    }

    @GetMapping("/payments")
    public ResponseEntity<PageResponse<PaymentResponse>> listPayments(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(paymentFacadeService.listPayments(currentUser, page, size));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(paymentFacadeService.getPayment(currentUser, paymentId));
    }

    @PostMapping("/payments/webhooks/yookassa")
    public ResponseEntity<PaymentResponse> yookassaWebhook(
            @RequestHeader(value = "X-Webhook-Token", required = false) String webhookToken,
            @Valid @RequestBody PaymentWebhookRequest request
    ) {
        return ResponseEntity.ok(paymentFacadeService.handleYooKassaWebhook(request, webhookToken));
    }
}
