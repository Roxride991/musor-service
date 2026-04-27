package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class ServiceZoneResponse {
    Long id;
    String name;
    boolean active;
    List<CoordinateResponse> coordinates;
    OffsetDateTime createdAt;

    @Value
    @Builder
    public static class CoordinateResponse {
        double lat;
        double lng;
    }
}
