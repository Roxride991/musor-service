package com.example.core.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service_zones")
public class ServiceZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // Например: "Оренбург"
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coordinates", columnDefinition = "jsonb")
    private List<Coordinate> coordinates;

    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Внутренний класс для хранения координат точки зоны.
     * Используется в JSON-массиве: [{"lat": 51.76, "lng": 55.09}, ...]
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate {
        private double lat;
        private double lng;
    }
}