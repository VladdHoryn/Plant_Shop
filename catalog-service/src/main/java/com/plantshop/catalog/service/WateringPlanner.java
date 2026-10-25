package com.plantshop.catalog.service;

import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.LightRequirement;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

import static com.plantshop.catalog.entity.LightRequirement.BRIGHT_INDIRECT;
import static com.plantshop.catalog.entity.LightRequirement.DIRECT_SUN;
import static com.plantshop.catalog.entity.LightRequirement.LOW;
import static com.plantshop.catalog.entity.LightRequirement.MEDIUM;

/** How often a plant needs water, from its care level and the light it gets. */
@Component
public class WateringPlanner {

    /** Watering interval in days: care level -> light -> days. Adding a rule means adding data, not a branch. */
    private static final Map<CareLevel, Map<LightRequirement, Integer>> INTERVAL_DAYS = new EnumMap<>(Map.of(
            CareLevel.EASY, new EnumMap<>(Map.of(LOW, 14, MEDIUM, 10, BRIGHT_INDIRECT, 8, DIRECT_SUN, 6)),
            CareLevel.MEDIUM, new EnumMap<>(Map.of(LOW, 9, MEDIUM, 7, BRIGHT_INDIRECT, 5, DIRECT_SUN, 4)),
            CareLevel.HARD, new EnumMap<>(Map.of(LOW, 6, MEDIUM, 5, BRIGHT_INDIRECT, 3, DIRECT_SUN, 2))));

    public int wateringIntervalDays(CareLevel care, LightRequirement light) {
        return INTERVAL_DAYS.get(care).get(light);
    }
}
