package com.jansetu.common.api;

import com.jansetu.common.web.RequestIds;
import java.time.Instant;

/** Standard success envelope: {@code {"data": ..., "meta": {"requestId": ..., "timestamp": ...}}}. */
public record ApiResponse<T>(T data, Meta meta) {

    public record Meta(String requestId, Instant timestamp) {
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(RequestIds.current(), Instant.now()));
    }
}
