package com.plantshop.catalog.care;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lab 8, task 3: the observable advice of the three care guides. Plain JUnit 5, no Spring context. */
class CareGuideTest {

    @Test
    void anOrdinaryPlantIsWateredWeekly() {
        final PlantCareGuide guide = new PlantCareGuide();

        assertThat(guide.needsWater(6)).isFalse();
        assertThat(guide.needsWater(7)).isTrue();
        assertThat(guide.summary()).isEqualTo("Water every 7 days; Bright indirect light; keep above 15 C");
    }

    @Test
    void aTropicalPlantNeedsWaterTwoDaysEarlierAndHigherHumidity() {
        final TropicalCareGuide guide = new TropicalCareGuide();

        assertThat(guide.needsWater(4)).isFalse();
        assertThat(guide.needsWater(5)).isTrue();
        assertThat(guide.mistingPerWeek()).isEqualTo(5);
        assertThat(guide.summary())
                .isEqualTo("Water every 7 days; Bright indirect light; keep above 18 C; High humidity, mist daily");
    }

    @Test
    void anOrchidCombinesTropicalRulesWithItsOwnValues() {
        final OrchidCareGuide guide = new OrchidCareGuide();

        assertThat(guide.needsWater(7)).isFalse();
        assertThat(guide.needsWater(8)).isTrue();
        assertThat(guide.fertilizeEveryWeeks()).isEqualTo(2);
        assertThat(guide.repotEveryYears()).isEqualTo(3);
        assertThat(guide.mistingPerWeek()).isEqualTo(3);
        assertThat(guide.bloomRestWeeks()).isEqualTo(6);
        assertThat(guide.humidityAdvice()).isEqualTo("High humidity, mist daily");
        assertThat(guide.summary()).isEqualTo(
                "Water every 10 days; Bright light, no direct midday sun; keep above 18 C; High humidity, mist daily");
    }
}
