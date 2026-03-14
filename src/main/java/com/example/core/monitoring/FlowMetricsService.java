package com.example.core.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
public class FlowMetricsService {

    private static final String FLOW_AUTH = "auth";
    private static final String FLOW_ORDER_CREATE = "order_create";
    private static final String FLOW_TELEGRAM_VERIFY = "telegram_verify";

    private final MeterRegistry meterRegistry;

    private final ConcurrentLinkedQueue<Long> authSuccessEvents = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> authFailureEvents = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<Long> orderCreateSuccessEvents = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> orderCreateFailureEvents = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<Long> telegramVerifySuccessEvents = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> telegramVerifyFailureEvents = new ConcurrentLinkedQueue<>();

    public void recordAuthSuccess() {
        recordSuccess(FLOW_AUTH, authSuccessEvents);
    }

    public void recordAuthFailure() {
        recordFailure(FLOW_AUTH, authFailureEvents);
    }

    public void recordOrderCreateSuccess() {
        recordSuccess(FLOW_ORDER_CREATE, orderCreateSuccessEvents);
    }

    public void recordOrderCreateFailure() {
        recordFailure(FLOW_ORDER_CREATE, orderCreateFailureEvents);
    }

    public void recordTelegramVerifySuccess() {
        recordSuccess(FLOW_TELEGRAM_VERIFY, telegramVerifySuccessEvents);
    }

    public void recordTelegramVerifyFailure() {
        recordFailure(FLOW_TELEGRAM_VERIFY, telegramVerifyFailureEvents);
    }

    public double authFailureRate(Duration window) {
        return failureRate(window, authSuccessEvents, authFailureEvents);
    }

    public double orderCreateFailureRate(Duration window) {
        return failureRate(window, orderCreateSuccessEvents, orderCreateFailureEvents);
    }

    public double telegramVerifyFailureRate(Duration window) {
        return failureRate(window, telegramVerifySuccessEvents, telegramVerifyFailureEvents);
    }

    public long authTotalEvents(Duration window) {
        return total(window, authSuccessEvents, authFailureEvents);
    }

    public long orderCreateTotalEvents(Duration window) {
        return total(window, orderCreateSuccessEvents, orderCreateFailureEvents);
    }

    public long telegramVerifyTotalEvents(Duration window) {
        return total(window, telegramVerifySuccessEvents, telegramVerifyFailureEvents);
    }

    private void recordSuccess(String flow, ConcurrentLinkedQueue<Long> queue) {
        record(flow, "success", queue);
    }

    private void recordFailure(String flow, ConcurrentLinkedQueue<Long> queue) {
        record(flow, "failure", queue);
    }

    private void record(String flow, String outcome, ConcurrentLinkedQueue<Long> queue) {
        long now = System.currentTimeMillis();
        queue.add(now);
        meterRegistry.counter("core.flow.requests.total", "flow", flow, "outcome", outcome).increment();
    }

    private double failureRate(
            Duration window,
            ConcurrentLinkedQueue<Long> successEvents,
            ConcurrentLinkedQueue<Long> failureEvents
    ) {
        long success = countWithinWindow(successEvents, window);
        long failure = countWithinWindow(failureEvents, window);
        long total = success + failure;
        if (total == 0) {
            return 0.0;
        }
        return (double) failure / total;
    }

    private long total(
            Duration window,
            ConcurrentLinkedQueue<Long> successEvents,
            ConcurrentLinkedQueue<Long> failureEvents
    ) {
        return countWithinWindow(successEvents, window) + countWithinWindow(failureEvents, window);
    }

    private long countWithinWindow(ConcurrentLinkedQueue<Long> events, Duration window) {
        long cutoff = System.currentTimeMillis() - window.toMillis();
        while (true) {
            Long head = events.peek();
            if (head == null || head >= cutoff) {
                break;
            }
            events.poll();
        }
        return events.size();
    }
}
