package com.plantshop.catalog.care;

/** Tropical plants: warmer, more humid, their soil dries faster. Second level of the Lab 8 hierarchy. */
public class TropicalCareGuide extends PlantCareGuide {

    @Override
    public String humidityAdvice() {
        return "High humidity, mist daily";
    }

    @Override
    public int minTemperatureCelsius() {
        return 18;
    }

    /** The substrate of tropical plants dries two days earlier than the watering interval says. */
    @Override
    public boolean needsWater(final int daysSinceWatering) {
        return daysSinceWatering >= wateringIntervalDays() - 2;
    }

    @Override
    public String summary() {
        return super.summary() + "; " + humidityAdvice();
    }

    public int mistingPerWeek() {
        return 5;
    }
}
