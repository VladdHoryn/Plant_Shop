package com.plantshop.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Takes the correlation id from the {@value CorrelationId#HEADER} request header (or generates one),
 * puts it into the MDC for the duration of the request and echoes it in the response.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    // Only well-formed ids are accepted, so a client cannot inject arbitrary text into every log line
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(CorrelationId.HEADER);
        String correlationId = header != null && VALID_ID.matcher(header).matches()
                ? header
                : UUID.randomUUID().toString();
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
