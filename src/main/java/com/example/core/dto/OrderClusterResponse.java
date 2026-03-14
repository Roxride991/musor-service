package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class OrderClusterResponse {
    String clusterId;
    int orderCount;
    String pickupDate;
    String pickupSlot;
    double centroidLat;
    double centroidLng;
    OffsetDateTime pickupFrom;
    OffsetDateTime pickupTo;
    List<Long> orderIds;
    List<String> addresses;
}
