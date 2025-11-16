package com.example.core.dto;

import com.example.core.model.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public class CreateSubscriptionRequest {
    @NotNull
    private SubscriptionPlan plan;

    // геттеры и сеттеры
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }
}