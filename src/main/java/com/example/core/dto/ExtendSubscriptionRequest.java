package com.example.core.dto;

import com.example.core.model.SubscriptionPlan;

public class ExtendSubscriptionRequest {
    private SubscriptionPlan plan;

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
    }
}
