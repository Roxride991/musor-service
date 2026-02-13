package com.example.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionPlanTest {

    @Test
    void weeklyDescriptionShouldMatchTwoWeekDuration() {
        assertTrue(SubscriptionPlan.WEEKLY.getDescription().contains("14 дней"));
    }

    @Test
    void quarterlyAndYearlyPlansShouldHaveExpectedLimits() {
        assertEquals(45, SubscriptionPlan.QUARTERLY.getTotalOrders());
        assertEquals(182, SubscriptionPlan.YEARLY.getTotalOrders());
    }
}
