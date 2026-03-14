package com.example.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Запрос на создание/обновление активной зоны обслуживания.
 */
public class CreateServiceZoneRequest {

    @NotBlank
    private String name;

    @NotEmpty
    @Size(min = 3, message = "Полигон зоны должен содержать минимум 3 точки")
    private List<@Valid CoordinateDto> coordinates;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<CoordinateDto> getCoordinates() { return coordinates; }
    public void setCoordinates(List<CoordinateDto> coordinates) { this.coordinates = coordinates; }

    public static class CoordinateDto {
        @NotNull
        @DecimalMin(value = "-90.0", inclusive = true, message = "Широта должна быть в диапазоне [-90, 90]")
        @DecimalMax(value = "90.0", inclusive = true, message = "Широта должна быть в диапазоне [-90, 90]")
        private Double lat;
        @NotNull
        @DecimalMin(value = "-180.0", inclusive = true, message = "Долгота должна быть в диапазоне [-180, 180]")
        @DecimalMax(value = "180.0", inclusive = true, message = "Долгота должна быть в диапазоне [-180, 180]")
        private Double lng;
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }
}


