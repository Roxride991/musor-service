package com.example.core.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AcceptOrderClusterRequest {

    @NotEmpty(message = "Список заказов кластера не может быть пустым")
    private List<Long> orderIds;

    private Double radiusMeters;
}
