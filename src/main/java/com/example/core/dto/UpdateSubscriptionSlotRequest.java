package com.example.core.dto;

import com.example.core.model.PickupSlot;
import jakarta.validation.constraints.NotNull;

public class UpdateSubscriptionSlotRequest {

    @NotNull
    private PickupSlot pickupSlot;

    public PickupSlot getPickupSlot() {
        return pickupSlot;
    }

    public void setPickupSlot(PickupSlot pickupSlot) {
        this.pickupSlot = pickupSlot;
    }
}
