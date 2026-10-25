package com.plantshop.logging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class SecureLoggingAutoConfigurationTest {

    private static final AutoConfigurations CONFIG = AutoConfigurations.of(SecureLoggingAutoConfiguration.class);

    /** Runs before every other filter (Spring Security included), so even a 401 carries a correlation id. */
    @Test
    void registersTheCorrelationFilterFirstInServletApplications() {
        new WebApplicationContextRunner().withConfiguration(CONFIG).run(context -> {
            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(CorrelationIdFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        });
    }

    @Test
    void staysOutOfNonWebApplications() {
        new ApplicationContextRunner().withConfiguration(CONFIG)
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }
}
