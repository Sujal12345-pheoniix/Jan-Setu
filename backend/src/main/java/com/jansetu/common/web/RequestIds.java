package com.jansetu.common.web;

import org.slf4j.MDC;

/** Access to the correlation id of the current request (stored in the logging MDC). */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
