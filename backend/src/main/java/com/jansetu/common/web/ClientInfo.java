package com.jansetu.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Network metadata of the caller, truncated to column sizes. The remote address is used as-is; behind a reverse
 * proxy configure {@code server.forward-headers-strategy} so it reflects the real client.
 */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final ClientInfo NONE = new ClientInfo(null, null);

    public static ClientInfo from(HttpServletRequest request) {
        if (request == null) {
            return NONE;
        }
        return new ClientInfo(truncate(request.getRemoteAddr(), 45), truncate(request.getHeader("User-Agent"), 255));
    }

    /** Client info of the request bound to the current thread, or empty values outside a web request. */
    public static ClientInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return from(attributes.getRequest());
        }
        return NONE;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
