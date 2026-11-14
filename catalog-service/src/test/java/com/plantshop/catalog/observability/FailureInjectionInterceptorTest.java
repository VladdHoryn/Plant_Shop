package com.plantshop.catalog.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureInjectionInterceptorTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/plants");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void zeroFailRatePassesEveryRequest() throws Exception {
        FailureInjectionInterceptor interceptor = new FailureInjectionInterceptor(0, () -> 0.0);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void randomBelowFailRateAnswers500() throws Exception {
        FailureInjectionInterceptor interceptor = new FailureInjectionInterceptor(0.5, () -> 0.49);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("injected failure");
    }

    @Test
    void randomAtOrAboveFailRatePasses() throws Exception {
        FailureInjectionInterceptor interceptor = new FailureInjectionInterceptor(0.5, () -> 0.5);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void failRateOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> new FailureInjectionInterceptor(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
