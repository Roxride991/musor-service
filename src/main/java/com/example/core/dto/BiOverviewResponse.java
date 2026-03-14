package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Value
@Builder
public class BiOverviewResponse {
    OffsetDateTime generatedAt;
    BigDecimal totalRevenue;
    BigDecimal revenueLast30d;
    BigDecimal ltv;
    long payingUsers;
    long activeSubscriptions;
    long canceledSubscriptionsLast30d;
    double churnRate30d;
    long completedOrders30d;
    long cancelledOrders30d;
    double completionRate30d;
}
