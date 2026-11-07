package com.plantshop.catalog.care;

import java.time.Duration;

/**
 * Care advice for an ordinary house plant, as shown on a plant's page in the shop.
 * Lab 8, task 3: top of a deliberately deep inheritance chain (PlantCareGuide -> TropicalCareGuide ->
 * OrchidCareGuide). {@link #summary()} and {@link #needsWater(int)} are template methods: they call methods that
 * subclasses override, so their result depends on the whole chain.
 */
public class PlantCareGuide {

    /** Lab 8 FAIL experiment: intervals become java.time.Duration (was: int wateringIntervalDays()). */
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
