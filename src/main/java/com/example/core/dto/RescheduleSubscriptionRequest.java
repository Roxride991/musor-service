package com.example.core.dto;

import com.example.core.model.PickupSlot;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RescheduleSubscriptionRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextPickupDate;

    private PickupSlot pickupSlot;

    private String reason;
}
