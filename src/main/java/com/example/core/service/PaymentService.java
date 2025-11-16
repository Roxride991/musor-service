package com.example.core.service;

import com.example.core.model.Order;
import com.example.core.model.Payment;
import com.example.core.model.PaymentStatus;
import com.example.core.model.PaymentType;
import com.example.core.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Сервис платежей: создание, отметка об успешной оплате и отмена (заглушка).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /** Создаёт платёж для заказа; в MVP сразу помечается как успешный. */
    @Transactional
    public Payment createPaymentForOrder(Order order, BigDecimal amount) {
        Payment payment = Payment.builder()
                .type(PaymentType.ONE_TIME)
                .status(PaymentStatus.PENDING)
                .amount(amount)
                .order(order)
                .externalId("mock_" + UUID.randomUUID()) // Имитация externalId от платёжной системы
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // MVP: сразу помечаем как оплачено (заглушка вместо реального платежа)
        return markPaymentAsSucceeded(savedPayment.getExternalId());
    }

    /** Имитирует успешную оплату (в реальности — колбек платёжного провайдера). */
    @Transactional
    public Payment markPaymentAsSucceeded(String externalId) {
        Payment payment = paymentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: " + externalId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return payment; // Уже оплачен
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        return paymentRepository.save(payment);
    }

    /** Заглушка отмены платежа (в текущем MVP почти не используется). */
    @Transactional
    public Payment cancelPayment(String externalId) {
        Payment payment = paymentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: " + externalId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Нельзя отменить успешный платёж");
        }

        payment.setStatus(PaymentStatus.CANCELED);
        return paymentRepository.save(payment);
    }
}