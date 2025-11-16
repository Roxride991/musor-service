package com.example.core.dto;

import com.example.core.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO для ответа с информацией о заказе.
 * Безопасная версия: не содержит телефонов пользователей.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String address;
    private OffsetDateTime pickupTime;
    private String comment;
    private OrderStatus status;
    private BigDecimal price;
    private OffsetDateTime createdAt;
    private String message;

    // Информация о клиенте (только имя)
    private String clientName;

    // Информация о курьере (только имя, если назначен)
    private String courierName;

    // Флаг для различения свободных и активных заказов (для курьера)
    // true - свободный заказ (доступен для взятия)
    // false - активный заказ (принят курьером)
    private Boolean isAvailable;
}