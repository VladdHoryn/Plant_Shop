package com.plantshop.catalog.care;

import java.time.Duration;

/**
 * Orchids (Phalaenopsis): a tropical plant that is watered rarely and rests after blooming. It does not
 * extend TropicalCareGuide: it holds it as a private field and delegates. The tropical watering rule and summary
 * are applied to the orchid's own interval explicitly ({@code tropical.isDue(days, wateringInterval())}) instead
 * of silently, through an overridden method called from inherited code.
 */
public class OrchidCareGuide {

    private final TropicalCareGuide tropical;

    public OrchidCareGuide(final TropicalCareGuide tropical) {
        this.tropical = tropical;
    }

    public Duration wateringInterval() {
        return Duration.ofDays(10);
    }

    public String lightAdvice() {
        return "Bright light, no direct midday sun";
    }

    public String humidityAdvice() {
        return tropical.humidityAdvice();
    }

    public int minTemperatureCelsius() {
        return tropical.minTemperatureCelsius();
    }

    public int fertilizeEveryWeeks() {
        return 2;
    }

    public int repotEveryYears() {
        return 3;
    }

    public boolean needsWater(final int daysSinceWatering) {
        return tropical.isDue(daysSinceWatering, wateringInterval());
    }

    public String summary() {
        return tropical.describe(wateringInterval(), lightAdvice());
    }

    public int mistingPerWeek() {
        return 3;
    }

    public int bloomRestWeeks() {
        return 6;
    }
}
