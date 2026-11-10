package com.kyro.platform.common.observability;

import org.slf4j.MDC;

/** Access to the current request's correlation id (stored in MDC key {@code correlationId}). */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationContext() {}

    /** The current correlation id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
