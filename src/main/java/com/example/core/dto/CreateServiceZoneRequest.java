package com.example.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Запрос на создание/обновление активной зоны обслуживания.
 */
public class CreateServiceZoneRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<CoordinateDto> coordinates;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<CoordinateDto> getCoordinates() { return coordinates; }
    public void setCoordinates(List<CoordinateDto> coordinates) { this.coordinates = coordinates; }

    public static class CoordinateDto {
        @NotNull
        private Double lat;
        @NotNull
        private Double lng;
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }
}


