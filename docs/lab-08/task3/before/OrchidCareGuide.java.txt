package com.plantshop.catalog.care;

/**
 * Orchids (Phalaenopsis): a tropical plant that is watered rarely and rests after blooming. Third level of the
 * Lab 8 hierarchy; overrides half of what it inherits, and the inherited template methods silently pick up those
 * overrides (its needsWater and summary are TropicalCareGuide's code running on OrchidCareGuide's values).
 */
public class OrchidCareGuide extends TropicalCareGuide {

    @Override
    public int wateringIntervalDays() {
        return 10;
    }

    @Override
    public String lightAdvice() {
        return "Bright light, no direct midday sun";
    }

    @Override
    public int fertilizeEveryWeeks() {
        return 2;
    }

    @Override
    public int repotEveryYears() {
        return 3;
    }

    @Override
    public int mistingPerWeek() {
        return 3;
    }

    public int bloomRestWeeks() {
        return 6;
    }
}
