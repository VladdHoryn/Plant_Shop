package com.plantshop.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> seenInChain = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> seenInChain.set(CorrelationId.current());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsAValidIdForTheRequestAndRemovesItAfterwards() throws Exception {
        request.addHeader(CorrelationId.HEADER, "demo-correlation-0001");

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).isEqualTo("demo-correlation-0001");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("demo-correlation-0001");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        assertThat(CorrelationId.current()).isEqualTo("none");
    }

    @Test
    void generatesAnIdWhenTheHeaderIsMissing() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(seenInChain.get());
    }

    /** A header with a line break would let a client forge log lines; it is replaced, not used. */
    @Test
    void replacesAnIdThatCouldInjectIntoTheLog() throws Exception {
        request.addHeader(CorrelationId.HEADER, "abc12345\nERROR forged line");

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).matches("[0-9a-f-]{36}");
    }

    @Test
    void rejectsIdsThatAreTooShort() throws Exception {
        request.addHeader(CorrelationId.HEADER, "abc");

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).isNotEqualTo("abc").matches("[0-9a-f-]{36}");
    }
}
