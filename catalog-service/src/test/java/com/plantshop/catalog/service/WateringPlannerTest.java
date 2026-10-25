package com.plantshop.catalog.service;

import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.LightRequirement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WateringPlannerTest {

    private final WateringPlanner planner = new WateringPlanner();

    /** Every combination of care level and light: the contract for any implementation. */
    @ParameterizedTest(name = "{0} + {1} -> every {2} days")
    @CsvSource({
            "EASY,   LOW,             14", "EASY,   MEDIUM, 10", "EASY,   BRIGHT_INDIRECT, 8", "EASY,   DIRECT_SUN, 6",
            "MEDIUM, LOW,              9", "MEDIUM, MEDIUM,  7", "MEDIUM, BRIGHT_INDIRECT, 5", "MEDIUM, DIRECT_SUN, 4",
            "HARD,   LOW,              6", "HARD,   MEDIUM,  5", "HARD,   BRIGHT_INDIRECT, 3", "HARD,   DIRECT_SUN, 2"
    })
    void wateringIntervalFollowsCareAndLight(CareLevel care, LightRequirement light, int days) {
        assertThat(planner.wateringIntervalDays(care, light)).isEqualTo(days);
    }
}
