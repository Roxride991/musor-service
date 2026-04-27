package com.example.core.service;

import com.example.core.dto.PaymentCheckoutResponse;
import com.example.core.dto.PaymentWebhookRequest;
import com.example.core.model.Order;
import com.example.core.model.Payment;
import com.example.core.model.PaymentMode;
import com.example.core.model.PaymentProviderKind;
import com.example.core.model.PaymentStatus;
import com.example.core.model.PaymentType;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.PaymentRepository;
import com.example.core.service.payment.MockPaymentGatewayClient;
import com.example.core.service.payment.PaymentCreateCommand;
import com.example.core.service.payment.PaymentGatewayClient;
import com.example.core.service.payment.PaymentGatewayResult;
import com.example.core.service.payment.YooKassaPaymentGatewayClient;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Реальная интеграция платежей с поддержкой mock и YooKassa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MockPaymentGatewayClient mockGatewayClient;
    private final YooKassaPaymentGatewayClient yooKassaPaymentGatewayClient;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Value("${payments.provider:MOCK}")
    private String providerRaw;

    @Value("${payments.mode:HYBRID}")
    private String paymentModeRaw;

    @Value("${payments.webhook-token:}")
    private String webhookToken;

    @Value("${payments.default-return-url:http://localhost:5173/profile}")
    private String defaultReturnUrl;

    @Value("${payments.one-time-order-amount-rub:350}")
    private BigDecimal oneTimeOrderAmountRub;

    @PostConstruct
    void validateConfiguration() {
        PaymentProviderKind providerKind = getProviderKind();
        if (providerKind != PaymentProviderKind.MOCK && (webhookToken == null || webhookToken.isBlank())) {
            throw new IllegalStateException("payments.webhook-token must be configured for non-mock providers");
        }
    }

    @Transactional
    public Payment createPaymentForOrder(Order order, BigDecimal amount) {
        BigDecimal resolvedAmount = amount == null ? resolveOrderAmount(order) : amount;
        PaymentCheckoutResponse response = initOrderPayment(null, order, resolvedAmount, null, "Оплата заказа");
        return paymentRepository.findById(response.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("Платеж не найден после инициализации"));
    }

    public BigDecimal resolveOrderAmount(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Заказ не найден");
        }
        return normalizeAmount(oneTimeOrderAmountRub);
    }

    public BigDecimal resolveSubscriptionAmount(Subscription subscription) {
        if (subscription == null || subscription.getId() == null) {
            throw new IllegalArgumentException("Подписка не найдена");
        }
        if (subscription.getPrice() == null) {
            throw new IllegalStateException("Не удалось определить стоимость подписки");
        }
        return normalizeAmount(subscription.getPrice());
    }

    @Transactional
    public Payment markPaymentAsSucceeded(String externalId) {
        Payment payment = paymentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: " + externalId));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setProviderPayload("{\"manual\":true,\"status\":\"SUCCEEDED\"}");
            payment = paymentRepository.save(payment);
            notifyPaymentSucceeded(payment);
        }

        return payment;
    }

    @Transactional
    public Payment cancelPayment(String externalId) {
        Payment payment = paymentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: " + externalId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Нельзя отменить успешный платёж");
        }

        payment.setStatus(PaymentStatus.CANCELED);
        payment.setProviderPayload("{\"manual\":true,\"status\":\"CANCELED\"}");
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentCheckoutResponse initOrderPayment(
            User actor,
            Order order,
            BigDecimal amount,
            String returnUrl,
            String description
    ) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Заказ не найден");
        }

        Payment payment = paymentRepository.findFirstByOrderId(order.getId())
                .orElseGet(() -> Payment.builder()
                        .order(order)
                        .type(PaymentType.ONE_TIME)
                        .status(PaymentStatus.PENDING)
                        .build());

        if (payment.getId() != null && payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return toCheckoutResponse(payment, "Платеж по заказу уже оплачен");
        }
        if (payment.getId() != null
                && payment.getStatus() == PaymentStatus.PENDING
                && hasText(payment.getExternalId())) {
            return toCheckoutResponse(payment, "Платеж по заказу уже создан");
        }

        Payment saved = initOrRefreshPayment(
                payment,
                PaymentType.ONE_TIME,
                amount,
                returnUrl,
                description,
                order.getId(),
                null,
                actor
        );
        if (saved.getStatus() == PaymentStatus.SUCCEEDED) {
            notifyPaymentSucceeded(saved);
        }

        return toCheckoutResponse(saved, "Платеж по заказу создан");
    }

    @Transactional
    public PaymentCheckoutResponse initSubscriptionPayment(
            User actor,
            Subscription subscription,
            BigDecimal amount,
            String returnUrl,
            String description
    ) {
        if (subscription == null || subscription.getId() == null) {
            throw new IllegalArgumentException("Подписка не найдена");
        }

        Payment payment = paymentRepository.findFirstBySubscriptionId(subscription.getId())
                .orElseGet(() -> Payment.builder()
                        .subscription(subscription)
                        .type(PaymentType.SUBSCRIPTION)
                        .status(PaymentStatus.PENDING)
                        .build());

        if (payment.getId() != null && payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return toCheckoutResponse(payment, "Платеж по подписке уже оплачен");
        }
        if (payment.getId() != null
                && payment.getStatus() == PaymentStatus.PENDING
                && hasText(payment.getExternalId())) {
            return toCheckoutResponse(payment, "Платеж по подписке уже создан");
        }

        Payment saved = initOrRefreshPayment(
                payment,
                PaymentType.SUBSCRIPTION,
                amount,
                returnUrl,
                description,
                null,
                subscription.getId(),
                actor
        );
        if (saved.getStatus() == PaymentStatus.SUCCEEDED) {
            notifyPaymentSucceeded(saved);
        }

        return toCheckoutResponse(saved, "Платеж по подписке создан");
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsForUser(User user) {
        return getPaymentsForUser(user, Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public Page<Payment> getPaymentsForUser(User user, Pageable pageable) {
        if (user.getUserRole() == UserRole.ADMIN) {
            return paymentRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return paymentRepository.findVisibleForUser(user.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentForUser(Long paymentId, User user) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден"));

        if (user.getUserRole() == UserRole.ADMIN || belongsToUser(payment, user.getId())) {
            return payment;
        }

        throw new IllegalStateException("Платеж недоступен");
    }

    @Transactional
    public Payment synchronizePaymentStatus(Long paymentId, User actor) {
        Payment payment = getPaymentForUser(paymentId, actor);
        if (payment.getExternalId() == null || payment.getExternalId().isBlank()) {
            return payment;
        }

        PaymentGatewayResult statusResult = resolveGateway().fetchPayment(payment.getExternalId());
        updatePaymentFromGateway(payment, statusResult);
        Payment saved = paymentRepository.save(payment);
        if (saved.getStatus() == PaymentStatus.SUCCEEDED) {
            notifyPaymentSucceeded(saved);
        }
        return saved;
    }

    @Transactional
    public Payment handleWebhook(PaymentWebhookRequest request, String tokenHeader) {
        validateWebhookToken(tokenHeader);
        if (request == null || request.getObject() == null) {
            throw new IllegalArgumentException("Webhook payload is empty");
        }

        JsonNode object = request.getObject();
        String externalId = object.path("id").asText(null);
        String statusRaw = object.path("status").asText(null);
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("Webhook payment id is missing");
        }

        Payment payment = paymentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден: " + externalId));

        PaymentStatus mapped = mapProviderStatus(statusRaw);
        payment.setStatus(mapped);
        payment.setProviderPayload(object.toString());
        Payment saved = paymentRepository.save(payment);

        auditService.log(
                "PAYMENT_WEBHOOK",
                "SUCCESS",
                null,
                "SYSTEM",
                "PAYMENT_ID",
                String.valueOf(saved.getId()),
                "Webhook processed: status=" + mapped,
                null
        );

        if (mapped == PaymentStatus.SUCCEEDED) {
            notifyPaymentSucceeded(saved);
        }

        return saved;
    }

    public PaymentProviderKind getProviderKind() {
        return parseProvider(providerRaw);
    }

    public PaymentMode getPaymentMode() {
        return parsePaymentMode(paymentModeRaw);
    }

    private Payment initOrRefreshPayment(
            Payment payment,
            PaymentType paymentType,
            BigDecimal amount,
            String returnUrl,
            String description,
            Long orderId,
            Long subscriptionId,
            User actor
    ) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String idempotenceKey = UUID.randomUUID().toString();
        PaymentGatewayResult gatewayResult = resolveGateway().createPayment(
                PaymentCreateCommand.builder()
                        .type(paymentType)
                        .amount(normalizedAmount)
                        .currency("RUB")
                        .returnUrl(resolveReturnUrl(returnUrl))
                        .description(description)
                        .orderId(orderId)
                        .subscriptionId(subscriptionId)
                        .idempotenceKey(idempotenceKey)
                        .build()
        );

        payment.setType(paymentType);
        payment.setAmount(normalizedAmount);
        payment.setCurrency("RUB");
        payment.setProvider(getProviderKind().name());
        payment.setIdempotenceKey(idempotenceKey);
        updatePaymentFromGateway(payment, gatewayResult);

        Payment saved = paymentRepository.save(payment);

        auditService.log(
                "PAYMENT_INIT",
                "SUCCESS",
                actor,
                "PAYMENT_ID",
                String.valueOf(saved.getId()),
                "Payment initialized: type=" + paymentType + ", status=" + saved.getStatus(),
                null
        );

        return saved;
    }

    private void updatePaymentFromGateway(Payment payment, PaymentGatewayResult gatewayResult) {
        payment.setExternalId(gatewayResult.getExternalId());
        payment.setStatus(gatewayResult.getStatus());
        payment.setConfirmationUrl(gatewayResult.getConfirmationUrl());
        payment.setProviderPayload(gatewayResult.getRawPayload());
    }

    private PaymentCheckoutResponse toCheckoutResponse(Payment payment, String message) {
        return PaymentCheckoutResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .provider(parseProvider(payment.getProvider()))
                .externalId(payment.getExternalId())
                .confirmationUrl(payment.getConfirmationUrl())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .message(message)
                .build();
    }

    private PaymentProviderKind parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return PaymentProviderKind.MOCK;
        }
        try {
            return PaymentProviderKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PaymentProviderKind.MOCK;
        }
    }

    private PaymentMode parsePaymentMode(String value) {
        if (value == null || value.isBlank()) {
            return PaymentMode.HYBRID;
        }
        try {
            return PaymentMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PaymentMode.HYBRID;
        }
    }

    private PaymentStatus mapProviderStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }
        String normalized = providerStatus.trim().toLowerCase();
        return switch (normalized) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            case "failed" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private PaymentGatewayClient resolveGateway() {
        PaymentProviderKind providerKind = getProviderKind();
        if (providerKind == PaymentProviderKind.YOOKASSA) {
            return yooKassaPaymentGatewayClient;
        }
        return mockGatewayClient;
    }

    private void validateWebhookToken(String tokenHeader) {
        if (getProviderKind() == PaymentProviderKind.MOCK) {
            return;
        }

        if (webhookToken == null || webhookToken.isBlank()) {
            throw new SecurityException("Webhook token is not configured");
        }
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new SecurityException("Webhook token is missing");
        }

        if (!constantTimeEquals(webhookToken.trim(), tokenHeader.trim())) {
            throw new SecurityException("Invalid webhook token");
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean belongsToUser(Payment payment, Long userId) {
        if (payment == null || userId == null) {
            return false;
        }

        if (payment.getOrder() != null && payment.getOrder().getClient() != null) {
            Long orderUserId = payment.getOrder().getClient().getId();
            if (Objects.equals(orderUserId, userId)) {
                return true;
            }
        }

        if (payment.getSubscription() != null && payment.getSubscription().getUser() != null) {
            Long subUserId = payment.getSubscription().getUser().getId();
            return Objects.equals(subUserId, userId);
        }

        return false;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Сумма платежа должна быть больше 0");
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String resolveReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return defaultReturnUrl;
        }
        return returnUrl.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void notifyPaymentSucceeded(Payment payment) {
        if (payment == null) {
            return;
        }

        if (payment.getOrder() != null && payment.getOrder().getClient() != null) {
            notificationService.enqueueInApp(
                    payment.getOrder().getClient(),
                    com.example.core.model.NotificationType.PAYMENT_STATUS,
                    "Оплата подтверждена",
                    "Платеж по заказу №" + payment.getOrder().getId() + " успешно завершен",
                    payment.getOrder().getId(),
                    null,
                    "payment-success-order-" + payment.getId()
            );
            return;
        }

        if (payment.getSubscription() != null && payment.getSubscription().getUser() != null) {
            notificationService.enqueueInApp(
                    payment.getSubscription().getUser(),
                    com.example.core.model.NotificationType.PAYMENT_STATUS,
                    "Оплата подписки подтверждена",
                    "Платеж по подписке №" + payment.getSubscription().getId() + " успешно завершен",
                    null,
                    payment.getSubscription().getId(),
                    "payment-success-sub-" + payment.getId()
            );
        }
    }
}
