package com.example.core.model;

import lombok.Getter;

import java.time.LocalTime;

@Getter
public enum PickupSlot {
    SLOT_8_11(LocalTime.of(8, 0), "08:00 - 11:00"),
    SLOT_13_16(LocalTime.of(13, 0), "13:00 - 16:00"),
    SLOT_19_21(LocalTime.of(19, 0), "19:00 - 21:00");

    private final LocalTime startTime;
    private final String label;

    PickupSlot(LocalTime startTime, String label) {
        this.startTime = startTime;
        this.label = label;
    }
}
