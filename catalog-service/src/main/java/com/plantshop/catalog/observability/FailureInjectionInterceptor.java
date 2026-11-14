package com.plantshop.catalog.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Lab 7 "bad release": answers this share of API requests with 500 instead of calling the controller.
 * The share comes from the FAIL_RATE environment variable (plantshop.fail-rate), 0 by default.
 * <p>
 * It is a HandlerInterceptor, not a servlet filter: the handler mapping has already matched the route,
 * so the failed requests keep their route template in the {@code uri} tag of the HTTP metrics and in the logs.
 * Registered only for {@code /api/**}, so health probes and {@code /actuator/prometheus} never fail.
 */
public class FailureInjectionInterceptor implements HandlerInterceptor {

    private final double failRate;
    private final DoubleSupplier random;

    public FailureInjectionInterceptor(double failRate) {
        this(failRate, () -> ThreadLocalRandom.current().nextDouble());
    }

    FailureInjectionInterceptor(double failRate, DoubleSupplier random) {
        if (failRate < 0 || failRate > 1) {
            throw new IllegalArgumentException("plantshop.fail-rate must be between 0 and 1, got " + failRate);
        }
        this.failRate = failRate;
        this.random = random;
    }

    public double getFailRate() {
        return failRate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (failRate == 0 || random.getAsDouble() >= failRate) {
            return true;
        }
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"injected failure\"}");
        return false;
    }
}
