package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OrderClusteringResponse {
    double radiusMeters;
    int sourceOrders;
    int clusteredOrders;
    int skippedWithoutCoordinates;
    List<OrderClusterResponse> clusters;
}
