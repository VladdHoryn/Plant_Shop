package com.plantshop.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalApiConfig {

    @Bean
    public FilterRegistrationBean<InternalApiTokenFilter> internalApiTokenFilter(
            @Value("${plantshop.internal-api.token}") String token) {
        FilterRegistrationBean<InternalApiTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalApiTokenFilter(token));
        registration.addUrlPatterns("/internal/*");
        return registration;
    }
}
