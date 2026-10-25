package com.plantshop.user.config;

import com.plantshop.logging.CorrelationId;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Feign calls to other services carry the shared internal-API token and the current correlation id. */
@Configuration
public class InternalApiClientConfig {

    public static final String TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public RequestInterceptor internalApiHeaders(@Value("${plantshop.internal-api.token}") String token) {
        return template -> template
                .header(TOKEN_HEADER, token)
                .header(CorrelationId.HEADER, CorrelationId.current());
    }
}
