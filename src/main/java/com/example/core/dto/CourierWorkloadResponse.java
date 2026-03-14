package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CourierWorkloadResponse {
    Long courierId;
    String courierName;
    String courierPhoneMasked;
    int activeOrders;
}
