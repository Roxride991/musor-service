package com.example.core.model;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum SubscriptionPlan {
    WEEKLY(7, "Недельная подписка: 14 дней, 7 вывозов", 690),
    MONTHLY(15, "Месячная подписка: 30 дней, 15 вывозов", 999);

    private final int totalOrders;
    private final String description;
    private final int priceRub;

    SubscriptionPlan(int totalOrders, String description, int priceRub) {
        this.totalOrders = totalOrders;
        this.description = description;
        this.priceRub = priceRub;
    }

    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceRub);
    }
}