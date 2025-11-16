package com.example.core.dto;

import com.example.core.model.SubscriptionPlan;
import com.example.core.model.SubscriptionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Value
@Builder
@Getter
@AllArgsConstructor
public class SubscriptionResponse {
    Long id;
    SubscriptionPlan plan;
    String planDescription;
    int totalAllowedOrders;
    int usedOrders;
    int remainingOrders;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate endDate;
    BigDecimal price;
    SubscriptionStatus status;
    OffsetDateTime createdAt;
}