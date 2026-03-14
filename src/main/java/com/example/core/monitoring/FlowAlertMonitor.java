package com.example.core.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowAlertMonitor {

    private final FlowMetricsService flowMetricsService;

    @Value("${monitoring.flow-alert.window-minutes:15}")
    private long windowMinutes;

    @Value("${monitoring.flow-alert.min-events:20}")
    private long minEvents;

    @Value("${monitoring.flow-alert.auth-failure-threshold:0.25}")
    private double authFailureThreshold;

    @Value("${monitoring.flow-alert.order-failure-threshold:0.20}")
    private double orderFailureThreshold;

    @Value("${monitoring.flow-alert.telegram-failure-threshold:0.30}")
    private double telegramFailureThreshold;

    @Scheduled(fixedDelayString = "${monitoring.flow-alert.period-ms:60000}")
    public void monitor() {
        Duration window = Duration.ofMinutes(Math.max(1, windowMinutes));
        checkFlow(
                "AUTH",
                flowMetricsService.authTotalEvents(window),
                flowMetricsService.authFailureRate(window),
                authFailureThreshold,
                window
        );
        checkFlow(
                "ORDER_CREATE",
                flowMetricsService.orderCreateTotalEvents(window),
                flowMetricsService.orderCreateFailureRate(window),
                orderFailureThreshold,
                window
        );
        checkFlow(
                "TELEGRAM_VERIFY",
                flowMetricsService.telegramVerifyTotalEvents(window),
                flowMetricsService.telegramVerifyFailureRate(window),
                telegramFailureThreshold,
                window
        );
    }

    private void checkFlow(String flow, long total, double failureRate, double threshold, Duration window) {
        if (total < minEvents) {
            return;
        }
        if (failureRate >= threshold) {
            log.error(
                    "ALERT flow_degradation: flow={}, failureRate={}, threshold={}, totalEvents={}, windowMinutes={}",
                    flow,
                    String.format("%.4f", failureRate),
                    String.format("%.4f", threshold),
                    total,
                    window.toMinutes()
            );
        }
    }
}
