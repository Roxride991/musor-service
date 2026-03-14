package com.example.core.service.payment;

import com.example.core.model.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class YooKassaPaymentGatewayClient implements PaymentGatewayClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payments.yookassa.api-url:https://api.yookassa.ru/v3}")
    private String apiUrl;

    @Value("${payments.yookassa.shop-id:}")
    private String shopId;

    @Value("${payments.yookassa.secret-key:}")
    private String secretKey;

    @Override
    public PaymentGatewayResult createPayment(PaymentCreateCommand command) {
        ensureConfigured();
        try {
            JsonNode body = buildCreatePayload(command);
            HttpHeaders headers = authHeaders(command.getIdempotenceKey());
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + "/payments",
                    HttpMethod.POST,
                    request,
                    String.class
            );
            return parseResponse(response.getBody());
        } catch (RestClientException e) {
            log.error("YooKassa create payment network error: {}", e.getMessage());
            throw new IllegalStateException("Платежный провайдер временно недоступен", e);
        } catch (Exception e) {
            log.error("YooKassa create payment error", e);
            throw new IllegalStateException("Не удалось создать платеж", e);
        }
    }

    @Override
    public PaymentGatewayResult fetchPayment(String externalId) {
        ensureConfigured();
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId is required");
        }

        try {
            HttpHeaders headers = authHeaders(null);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + "/payments/" + externalId,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            return parseResponse(response.getBody());
        } catch (RestClientException e) {
            log.error("YooKassa fetch payment network error: {}", e.getMessage());
            throw new IllegalStateException("Платежный провайдер временно недоступен", e);
        } catch (Exception e) {
            log.error("YooKassa fetch payment error", e);
            throw new IllegalStateException("Не удалось получить статус платежа", e);
        }
    }

    private JsonNode buildCreatePayload(PaymentCreateCommand command) {
        String currency = command.getCurrency() == null || command.getCurrency().isBlank()
                ? "RUB"
                : command.getCurrency().trim().toUpperCase();
        BigDecimal amount = command.getAmount() == null
                ? BigDecimal.ZERO
                : command.getAmount().setScale(2, RoundingMode.HALF_UP);

        var root = objectMapper.createObjectNode();
        var amountNode = root.putObject("amount");
        amountNode.put("value", amount.toPlainString());
        amountNode.put("currency", currency);
        root.put("capture", true);

        var confirmation = root.putObject("confirmation");
        confirmation.put("type", "redirect");
        String returnUrl = command.getReturnUrl() == null || command.getReturnUrl().isBlank()
                ? "https://localhost"
                : command.getReturnUrl().trim();
        confirmation.put("return_url", returnUrl);

        String description = command.getDescription() == null || command.getDescription().isBlank()
                ? "Оплата услуг Musor Service"
                : command.getDescription().trim();
        root.put("description", description);

        var metadata = root.putObject("metadata");
        if (command.getOrderId() != null) {
            metadata.put("orderId", String.valueOf(command.getOrderId()));
        }
        if (command.getSubscriptionId() != null) {
            metadata.put("subscriptionId", String.valueOf(command.getSubscriptionId()));
        }
        return root;
    }

    private HttpHeaders authHeaders(String idempotenceKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(shopId, secretKey);
        if (idempotenceKey != null && !idempotenceKey.isBlank()) {
            headers.set("Idempotence-Key", idempotenceKey);
        }
        return headers;
    }

    private PaymentGatewayResult parseResponse(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload == null ? "{}" : payload);
        String id = root.path("id").asText(null);
        String status = root.path("status").asText("");
        String confirmationUrl = root.path("confirmation").path("confirmation_url").asText(null);

        return PaymentGatewayResult.builder()
                .externalId(id)
                .status(mapStatus(status))
                .confirmationUrl(confirmationUrl)
                .rawPayload(payload)
                .build();
    }

    private PaymentStatus mapStatus(String status) {
        if ("succeeded".equalsIgnoreCase(status)) {
            return PaymentStatus.SUCCEEDED;
        }
        if ("pending".equalsIgnoreCase(status) || "waiting_for_capture".equalsIgnoreCase(status)) {
            return PaymentStatus.PENDING;
        }
        if ("canceled".equalsIgnoreCase(status)) {
            return PaymentStatus.CANCELED;
        }
        return PaymentStatus.FAILED;
    }

    private void ensureConfigured() {
        if (shopId == null || shopId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("YooKassa credentials are not configured");
        }
    }
}
