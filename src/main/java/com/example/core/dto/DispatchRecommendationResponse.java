package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class DispatchRecommendationResponse {
    Long orderId;
    String orderAddress;
    OffsetDateTime pickupTime;
    Long courierId;
    String courierName;
    String courierPhoneMasked;
    int activeOrders;
    boolean recommended;
    String reason;
}
