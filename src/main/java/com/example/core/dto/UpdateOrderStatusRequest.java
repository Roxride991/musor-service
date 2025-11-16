package com.example.core.dto;

import com.example.core.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на обновление статуса заказа.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {
    @NotNull(message = "Статус обязателен")
    private OrderStatus status;
}

