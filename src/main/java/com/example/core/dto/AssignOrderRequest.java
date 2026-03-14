package com.example.core.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignOrderRequest {

    @NotNull
    private Long courierId;

    private String reason;
}
