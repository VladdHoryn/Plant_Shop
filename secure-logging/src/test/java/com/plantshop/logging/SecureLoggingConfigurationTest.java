package com.plantshop.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static com.plantshop.logging.PiiMaskerTest.EMAIL;
import static com.plantshop.logging.PiiMaskerTest.PASSWORD;
import static com.plantshop.logging.PiiMaskerTest.PHONE;
import static org.assertj.core.api.Assertions.assertThat;

/** Boots Spring with the logback-spring.xml shipped in this module, exactly as every service does. */
@ExtendWith(OutputCaptureExtension.class)
class SecureLoggingConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class EmptyApplication {
    }

    @Test
    void sharedConfigurationMasksTheTextConsole(CapturedOutput output) {
        runAndLog();

        assertThat(output.getOut())
                .contains("Login o***@example.test phone=+44********23 password=[REDACTED]")
                .doesNotContain(EMAIL, PHONE, PASSWORD);
    }

    @Test
    void jsonLogsProfileWritesMaskedJson(CapturedOutput output) throws Exception {
        runAndLog("json-logs");

        String line = output.getOut().lines()
                .filter(l -> l.contains("\"logger_name\":\"login\""))
                .findFirst().orElseThrow();
        JsonNode json = new ObjectMapper().readTree(line);
        assertThat(json.get("message").asText())
                .isEqualTo("Login o***@example.test phone=+44********23 password=[REDACTED]");
        assertThat(output.getOut()).doesNotContain(EMAIL, PHONE, PASSWORD);
    }

    private void runAndLog(String... profiles) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(EmptyApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .profiles(profiles)
                .run()) {
            LoggerFactory.getLogger("login").info("Login {} phone={} password={}", EMAIL, PHONE, PASSWORD);
        }
    }
}
