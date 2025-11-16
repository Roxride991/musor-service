package com.example.core.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO для запроса на создание заказа.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotBlank(message = "Адрес обязателен")
    private String address;

    @NotNull(message = "Время вывоза обязательно")
    @Future(message = "Время вывоза должно быть в будущем")
    private OffsetDateTime pickupTime;

    private String comment;

    // Необязательный ID подписки; если указан — заказ создаётся по подписке
    private Long subscriptionId;

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }

    // Необязательные координаты адреса (если заданы — используем для проверки геозоны)
    private Double lat;
    private Double lng;

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}

