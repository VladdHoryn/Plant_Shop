package com.plantshop.catalog.care;

import java.time.Duration;

/**
 * Tropical plants: warmer, more humid, their soil dries faster. It does not extend PlantCareGuide.
 * The former parent is a private field injected through the constructor; what a tropical plant shares with an
 * ordinary one is delegated to it, what differs is this class's own code. Nothing is overridden, and the rule
 * that used to be reached through overriding (the watering interval) is now an explicit parameter of
 * {@link #isDue} and {@link #describe}.
 */
public class TropicalCareGuide {

    private final PlantCareGuide base;

    public TropicalCareGuide(final PlantCareGuide base) {
        this.base = base;
    }

    public Duration wateringInterval() {
        return base.wateringInterval();
    }

    public String lightAdvice() {
        return base.lightAdvice();
    }

    public String humidityAdvice() {
        return "High humidity, mist daily";
    }

    public int minTemperatureCelsius() {
        return 18;
    }

    public int fertilizeEveryWeeks() {
        return base.fertilizeEveryWeeks();
    }

    public int repotEveryYears() {
        return base.repotEveryYears();
    }

    public boolean needsWater(final int daysSinceWatering) {
        return isDue(daysSinceWatering, wateringInterval());
    }

    /** The tropical rule for any interval: the substrate dries two days before the interval ends. */
    public boolean isDue(final int daysSinceWatering, final Duration interval) {
        return daysSinceWatering >= interval.toDays() - 2;
    }

    public String summary() {
        return describe(wateringInterval(), lightAdvice());
    }

    public String describe(final Duration interval, final String light) {
        return base.describe(interval.toDays(), light, minTemperatureCelsius()) + "; " + humidityAdvice();
    }

    public int mistingPerWeek() {
        return 5;
    }
}
