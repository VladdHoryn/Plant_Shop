package com.plantshop.catalog.care;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lab 8, task 3: the observable advice of the three care guides. The expected values are the same as with the
 * inheritance chain (commit "add inheritance hierarchy baseline"), so the composition keeps the behaviour.
 * Plain JUnit 5, no Spring context.
 */
class CareGuideTest {

    private final PlantCareGuide plant = new PlantCareGuide();
    private final TropicalCareGuide tropical = new TropicalCareGuide(plant);
    private final OrchidCareGuide orchid = new OrchidCareGuide(tropical);

    @Test
    void anOrdinaryPlantIsWateredWeekly() {
        assertThat(plant.wateringInterval()).isEqualTo(Duration.ofDays(7));
        assertThat(plant.needsWater(6)).isFalse();
        assertThat(plant.needsWater(7)).isTrue();
        assertThat(plant.summary()).isEqualTo("Water every 7 days; Bright indirect light; keep above 15 C");
    }

    @Test
    void aTropicalPlantNeedsWaterTwoDaysEarlierAndHigherHumidity() {
        assertThat(tropical.needsWater(4)).isFalse();
        assertThat(tropical.needsWater(5)).isTrue();
        assertThat(tropical.mistingPerWeek()).isEqualTo(5);
        assertThat(tropical.fertilizeEveryWeeks()).isEqualTo(4);
        assertThat(tropical.summary())
                .isEqualTo("Water every 7 days; Bright indirect light; keep above 18 C; High humidity, mist daily");
    }

    @Test
    void anOrchidCombinesTropicalRulesWithItsOwnValues() {
        assertThat(orchid.needsWater(7)).isFalse();
        assertThat(orchid.needsWater(8)).isTrue();
        assertThat(orchid.fertilizeEveryWeeks()).isEqualTo(2);
        assertThat(orchid.repotEveryYears()).isEqualTo(3);
        assertThat(orchid.mistingPerWeek()).isEqualTo(3);
        assertThat(orchid.bloomRestWeeks()).isEqualTo(6);
        assertThat(orchid.humidityAdvice()).isEqualTo("High humidity, mist daily");
        assertThat(orchid.summary()).isEqualTo(
                "Water every 10 days; Bright light, no direct midday sun; keep above 18 C; High humidity, mist daily");
    }
}
