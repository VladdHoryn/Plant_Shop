package com.plantshop.catalog.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lab 7: access log and failure injection for the API endpoints only (not /actuator/**).
 * Order matters: the logging interceptor is first, so it also logs the requests that
 * FailureInjectionInterceptor stops with a 500.
 */
@Configuration
public class ObservabilityConfig implements WebMvcConfigurer {

    private final String version;
    private final double failRate;

    public ObservabilityConfig(@Value("${plantshop.version}") String version,
                               @Value("${plantshop.fail-rate:0}") double failRate) {
        this.version = version;
        this.failRate = failRate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor(version)).addPathPatterns("/api/**");
        registry.addInterceptor(new FailureInjectionInterceptor(failRate)).addPathPatterns("/api/**");
    }
}
