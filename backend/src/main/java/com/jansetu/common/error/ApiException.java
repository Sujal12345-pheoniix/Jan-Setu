package com.jansetu.common.error;

/**
 * Business/API exception carrying a stable {@link ErrorCode} and a detail message that is safe to show to clients.
 * Never put internal information (SQL, stack traces, secrets) in the detail.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String safeDetail) {
        super(safeDetail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static ApiException notFound(String resource) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, resource + " was not found.");
    }

    public static ApiException duplicate(String detail) {
        return new ApiException(ErrorCode.DUPLICATE_RESOURCE, detail);
    }

    public static ApiException invalidState(String detail) {
        return new ApiException(ErrorCode.INVALID_STATE, detail);
    }

    public static ApiException staleVersion() {
        return new ApiException(ErrorCode.STALE_VERSION,
                "The resource was changed by someone else. Reload it and try again.");
    }

    public static ApiException accessDenied(String detail) {
        return new ApiException(ErrorCode.ACCESS_DENIED, detail);
    }

    public static ApiException businessRule(String detail) {
        return new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, detail);
    }
}
