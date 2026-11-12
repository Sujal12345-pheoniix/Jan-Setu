package com.jansetu.common.api;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pagination and sorting query parameters ({@code ?page=0&size=20&sort=name,asc}). Sorting is restricted to an
 * allowlist that maps API field names to entity properties, so clients cannot sort on arbitrary columns.
 */
public record PageQuery(Integer page, Integer size, List<String> sort) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public Pageable toPageable(Map<String, String> sortableFields, Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "page must be zero or greater.");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "size must be between 1 and " + MAX_SIZE + ".");
        }
        return PageRequest.of(pageNumber, pageSize, parseSort(sortableFields, defaultSort));
    }

    private Sort parseSort(Map<String, String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isEmpty()) {
            return defaultSort;
        }
        List<Sort.Order> orders = new ArrayList<>();
        // Spring splits "name,asc" into ["name", "asc"]; accept both that form and "name,asc" strings.
        List<String> tokens = new ArrayList<>();
        sort.forEach(s -> tokens.addAll(List.of(s.split(","))));
        for (int i = 0; i < tokens.size(); i++) {
            String field = tokens.get(i).trim();
            String property = sortableFields.get(field);
            if (property == null) {
                throw new ApiException(ErrorCode.INVALID_PARAMETER,
                        "Unsupported sort field '" + field + "'. Allowed: " + String.join(", ", sortableFields.keySet()) + ".");
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (i + 1 < tokens.size()) {
                String next = tokens.get(i + 1).trim().toLowerCase(Locale.ROOT);
                if (next.equals("asc") || next.equals("desc")) {
                    direction = Sort.Direction.fromString(next);
                    i++;
                }
            }
            orders.add(new Sort.Order(direction, property));
        }
        if (orders.size() > 3) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "At most 3 sort fields are allowed.");
        }
        return Sort.by(orders);
    }
}
