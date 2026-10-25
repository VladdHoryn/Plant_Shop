package com.plantshop.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Guards the service-to-service endpoints under /internal/**: they are not routed by the gateway and
 * additionally require the shared token, so a client that reaches the pod directly cannot use them.
 */
@Slf4j
public class InternalApiTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Internal-Token";

    private final byte[] expectedToken;

    public InternalApiTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken.getBytes(UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(TOKEN_HEADER);
        // constant-time comparison, so the token cannot be guessed byte by byte from response times
        if (token == null || !MessageDigest.isEqual(expectedToken, token.getBytes(UTF_8))) {
            log.warn("Rejected internal API call without a valid token: {} {}", request.getMethod(),
                    request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
