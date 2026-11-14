package com.plantshop.catalog.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Lab 7 access log: one line per API request. With logging.structured.format.console=ecs every
 * line is a JSON object; the MDC keys below become top-level JSON fields, so Loki can filter on them
 * after {@code | json}: method, route, status, duration_ms, version.
 * <p>
 * The route is the matched template ({@code /api/catalog/plants/{id}}), never the raw path:
 * the same rule keeps metric labels low-cardinality.
 */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    static final String START_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".start";
    private static final Logger log = LoggerFactory.getLogger("http.access");

    private final String version;

    public RequestLoggingInterceptor(String version) {
        this.version = version;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        int status = response.getStatus();
        try {
            MDC.put("method", request.getMethod());
            MDC.put("route", route(request));
            MDC.put("status", String.valueOf(status));
            MDC.put("duration_ms", String.valueOf(durationMs(request)));
            MDC.put("version", version);
            if (status >= 500) {
                log.error("request failed");
            } else {
                log.info("request");
            }
        } finally {
            MDC.remove("method");
            MDC.remove("route");
            MDC.remove("status");
            MDC.remove("duration_ms");
            MDC.remove("version");
        }
    }

    static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "UNKNOWN";
    }

    static double durationMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_ATTRIBUTE);
        if (!(start instanceof Long startNanos)) {
            return 0;
        }
        return Math.round((System.nanoTime() - startNanos) / 100_000.0) / 10.0;
    }
}
