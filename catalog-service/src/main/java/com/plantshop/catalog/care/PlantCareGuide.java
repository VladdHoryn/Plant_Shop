package com.plantshop.catalog.care;

import java.time.Duration;

/**
 * Care advice for an ordinary house plant, as shown on a plant's page in the shop.
 * Lab 8, task 3: formerly the top of a 3-level inheritance chain; since the PASS commit TropicalCareGuide holds it
 * as a private field and delegates to it (composition), nothing extends it any more.
 */
public class PlantCareGuide {

    /** Since Lab 8 (FAIL commit) a Duration; it was int wateringIntervalDays(). */
    public Duration wateringInterval() {
        return Duration.ofDays(7);
    }

    public String lightAdvice() {
        return "Bright indirect light";
    }

    public String humidityAdvice() {
        return "Normal room humidity";
    }

    public int minTemperatureCelsius() {
        return 15;
    }

    public int fertilizeEveryWeeks() {
        return 4;
    }

    public int repotEveryYears() {
        return 2;
    }

    public boolean needsWater(final int daysSinceWatering) {
        return daysSinceWatering >= wateringInterval().toDays();
    }

    public String summary() {
        return describe(wateringInterval().toDays(), lightAdvice(), minTemperatureCelsius());
    }

    public String describe(final long wateringDays, final String light, final int minTemperature) {
        return "Water every " + wateringDays + " days; " + light + "; keep above " + minTemperature + " C";
    }
}
