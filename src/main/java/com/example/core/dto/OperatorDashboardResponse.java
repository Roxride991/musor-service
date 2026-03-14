package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class OperatorDashboardResponse {
    OffsetDateTime generatedAt;
    long publishedCount;
    long acceptedCount;
    long onTheWayCount;
    long pickedUpCount;
    long completedLast24h;
    long cancelledLast24h;
    long overdueOpenCount;
    long dueNextHourCount;
    double completionRate24h;
    double authFailureRate15m;
    double orderCreateFailureRate15m;
    double telegramVerifyFailureRate15m;
    List<CourierWorkloadResponse> courierWorkloads;
}
