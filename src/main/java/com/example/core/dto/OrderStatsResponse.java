package com.example.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для статистики заказов курьера.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatsResponse {
    private Long availableCount;  // Количество свободных заказов
    private Long activeCount;    // Количество активных заказов курьера
}

