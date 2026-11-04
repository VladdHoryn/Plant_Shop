package com.plantshop.catalog.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InfoControllerTest {

    private final InfoController controller = new InfoController("1.1.0", "Plant Shop", "UAH");

    @Test
    void versionReturnsTheBuildVersion() {
        assertThat(controller.version()).containsExactly(java.util.Map.entry("version", "1.1.0"));
    }

    @Test
    void infoContainsTheSameVersion() {
        assertThat(controller.info().version()).isEqualTo("1.1.0");
    }
}
