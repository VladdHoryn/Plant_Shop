package com.plantshop.logging;

import org.slf4j.MDC;

/** The id that ties together all log lines, audit records and downstream calls of one request. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : "none";
    }
}
