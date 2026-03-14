package com.example.core.service;

import com.example.core.dto.ReconciliationReportResponse;
import com.example.core.model.Payment;
import com.example.core.model.PaymentMode;
import com.example.core.model.PaymentStatus;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionStatus;
import com.example.core.repository.PaymentRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    @Value("${payments.mode:HYBRID}")
    private String paymentModeRaw;

    private final AtomicReference<ReconciliationReportResponse> lastReport = new AtomicReference<>();

    @Scheduled(cron = "${reconciliation.cron:0 */30 * * * *}")
    @Transactional
    public void scheduledRun() {
        ReconciliationReportResponse report = runNow();
        lastReport.set(report);
    }

    @Transactional
    public ReconciliationReportResponse runNow() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        LocalDate today = LocalDate.now();

        List<Subscription> expiredToFix = subscriptionRepository.findByStatusInAndEndDateBefore(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED),
                today
        );
        for (Subscription subscription : expiredToFix) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setPauseStartedAt(null);
            subscriptionRepository.save(subscription);
        }

        List<Subscription> overused = subscriptionRepository.findByStatusIn(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED)
        ).stream().filter(subscription -> subscription.getUsedOrders() > subscription.getTotalAllowedOrders()).toList();

        for (Subscription subscription : overused) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
        }

        List<Payment> orphanSucceeded = paymentRepository.findByStatus(PaymentStatus.SUCCEEDED).stream()
                .filter(payment -> payment.getOrder() == null && payment.getSubscription() == null)
                .toList();

        long unpaidActiveSubscriptions = 0L;
        PaymentMode mode = parseMode(paymentModeRaw);
        if (mode == PaymentMode.PREPAY || mode == PaymentMode.HYBRID) {
            unpaidActiveSubscriptions = subscriptionRepository.countWithoutSucceededPayment(SubscriptionStatus.ACTIVE);
        }

        OffsetDateTime finishedAt = OffsetDateTime.now();
        ReconciliationReportResponse report = ReconciliationReportResponse.builder()
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .expiredSubscriptionsFixed(expiredToFix.size())
                .overusedSubscriptionsFixed(overused.size())
                .orphanSucceededPayments(orphanSucceeded.size())
                .unpaidActiveSubscriptions(unpaidActiveSubscriptions)
                .totalIssues(expiredToFix.size() + overused.size() + orphanSucceeded.size() + unpaidActiveSubscriptions)
                .build();

        auditService.log(
                "RECONCILIATION_RUN",
                "SUCCESS",
                null,
                "SYSTEM",
                "RECONCILIATION",
                "latest",
                "issues=" + report.getTotalIssues(),
                null
        );

        log.info(
                "Reconciliation completed: expiredFixed={}, overusedFixed={}, orphanPayments={}, unpaidActive={}",
                report.getExpiredSubscriptionsFixed(),
                report.getOverusedSubscriptionsFixed(),
                report.getOrphanSucceededPayments(),
                report.getUnpaidActiveSubscriptions()
        );

        lastReport.set(report);
        return report;
    }

    public ReconciliationReportResponse getLastReport() {
        return lastReport.get();
    }

    private PaymentMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return PaymentMode.HYBRID;
        }
        try {
            return PaymentMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PaymentMode.HYBRID;
        }
    }
}
