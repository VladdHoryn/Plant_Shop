package com.plantshop.catalog.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingInterceptorTest {

    private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor("1.5.0");

    @Test
    void routeIsTheMatchedTemplateNotTheRawPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/plants/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/catalog/plants/{id}");

        assertThat(RequestLoggingInterceptor.route(request)).isEqualTo("/api/catalog/plants/{id}");
    }

    @Test
    void routeIsUnknownWithoutAMatchedHandler() {
        assertThat(RequestLoggingInterceptor.route(new MockHttpServletRequest("GET", "/nope"))).isEqualTo("UNKNOWN");
    }

    @Test
    void durationIsMeasuredFromPreHandle() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/plants");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getAttribute(RequestLoggingInterceptor.START_ATTRIBUTE)).isInstanceOf(Long.class);
        assertThat(RequestLoggingInterceptor.durationMs(request)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void afterCompletionCleansUpTheMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/plants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get("route")).isNull();
        assertThat(MDC.get("status")).isNull();
        assertThat(MDC.get("version")).isNull();
    }
}
