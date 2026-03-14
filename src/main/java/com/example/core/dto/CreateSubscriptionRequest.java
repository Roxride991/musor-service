package com.example.core.dto;

import com.example.core.model.PickupSlot;
import com.example.core.model.SubscriptionPlan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class CreateSubscriptionRequest {
    @NotNull
    private SubscriptionPlan plan;

    @NotBlank
    private String address;

    @NotNull
    private PickupSlot pickupSlot;

    // Опционально: если не задана, стартует сегодня.
    private LocalDate startDate;

    // Опциональные координаты адреса.
    private Double lat;
    private Double lng;

    // Опционально: 1 = каждый день, 2 = через день. По умолчанию 2.
    @Min(1)
    @Max(2)
    private Integer cadenceDays;

    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public PickupSlot getPickupSlot() { return pickupSlot; }
    public void setPickupSlot(PickupSlot pickupSlot) { this.pickupSlot = pickupSlot; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Integer getCadenceDays() { return cadenceDays; }
    public void setCadenceDays(Integer cadenceDays) { this.cadenceDays = cadenceDays; }
}
