package com.example.core.dto;

import com.example.core.model.OrderStatus;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class OrderAdminFilter {
    List<OrderStatus> statuses;
    OffsetDateTime pickupFrom;
    OffsetDateTime pickupTo;
    Long clientId;
    Long courierId;
    Boolean onlyUnassigned;
}
