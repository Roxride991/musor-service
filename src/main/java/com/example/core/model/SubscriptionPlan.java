package com.example.core.model;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum SubscriptionPlan {
    WEEKLY(7, "Двухнедельная подписка: 14 дней, 7 вывозов (через день)", 690),
    MONTHLY(15, "Месячная подписка: 30 дней, 15 вывозов (через день)", 999),
    QUARTERLY(45, "Квартальная подписка: 3 месяца, 45 вывозов (через день)", 2790),
    YEARLY(182, "Годовая подписка: 12 месяцев, 182 вывоза (через день)", 9990);

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
