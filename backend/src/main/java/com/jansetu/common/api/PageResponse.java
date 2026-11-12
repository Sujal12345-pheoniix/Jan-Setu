package com.jansetu.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Page envelope used by every collection endpoint. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().<T>map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return from(page, Function.identity());
    }
}
