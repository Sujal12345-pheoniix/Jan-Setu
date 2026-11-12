package com.jansetu.common.error;

import org.springframework.http.HttpStatus;

/** Stable, client-facing error codes. Never rename a code once released; clients may depend on it. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh session"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account is not active"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    PRIVILEGE_ESCALATION(HttpStatus.FORBIDDEN, "Privilege escalation not allowed"),
    CLIENT_HEADER_REQUIRED(HttpStatus.FORBIDDEN, "Client header required"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "Endpoint not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Duplicate resource"),
    STALE_VERSION(HttpStatus.CONFLICT, "Resource was modified concurrently"),
    INVALID_STATE(HttpStatus.CONFLICT, "Operation not allowed in current state"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Data conflict"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused with a different request"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Duplicate request in progress"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
