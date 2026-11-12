package com.jansetu.common.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into RFC 9457 problem details. Internal exception messages, SQL and stack traces are logged
 * server-side only and never returned to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ex.errorCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BindException.class) // includes MethodArgumentNotValidException
    ResponseEntity<ProblemDetail> handleBind(BindException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ProblemDetails.FieldError(e.getField(), safeMessage(e.getDefaultMessage(), e.getCode())))
                .toList();
        List<ProblemDetails.FieldError> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new ProblemDetails.FieldError(e.getObjectName(), safeMessage(e.getDefaultMessage(), e.getCode())))
                .toList();
        List<ProblemDetails.FieldError> all = new java.util.ArrayList<>(errors);
        all.addAll(globalErrors);
        return respond(ProblemDetails.build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.status(),
                "One or more fields are invalid.", request.getRequestURI(), all));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ProblemDetails.FieldError(
                                result.getMethodParameter().getParameterName(),
                                safeMessage(error.getDefaultMessage(), null))))
                .toList();
        return respond(ProblemDetails.build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.status(),
                "One or more parameters are invalid.", request.getRequestURI(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ProblemDetails.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return respond(ProblemDetails.build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.status(),
                "One or more fields are invalid.", request.getRequestURI(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String detail = "The request body is missing or is not valid JSON.";
        if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
            detail = "Unknown property '" + unknown.getPropertyName() + "'.";
        } else if (ex.getCause() instanceof InvalidFormatException invalid && !invalid.getPath().isEmpty()) {
            detail = "Invalid value for '" + invalid.getPath().getLast().getFieldName() + "'.";
        }
        return respond(ProblemDetails.build(ErrorCode.MALFORMED_REQUEST, detail, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ErrorCode.INVALID_PARAMETER,
                "Invalid value for parameter '" + ex.getName() + "'.", request.getRequestURI()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MissingRequestCookieException.class})
    ResponseEntity<ProblemDetail> handleMissing(Exception ex, HttpServletRequest request) {
        String name = switch (ex) {
            case MissingServletRequestParameterException e -> "parameter '" + e.getParameterName() + "'";
            case MissingRequestHeaderException e -> "header '" + e.getHeaderName() + "'";
            case MissingRequestCookieException e -> "cookie '" + e.getCookieName() + "'";
            default -> "value";
        };
        return respond(ProblemDetails.build(ErrorCode.INVALID_PARAMETER, "Required " + name + " is missing.",
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ErrorCode.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for this endpoint.", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported. Use application/json.", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ErrorCode.ENDPOINT_NOT_FOUND, "No endpoint exists at this path.",
                request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ErrorCode.PAYLOAD_TOO_LARGE, "The uploaded file is too large.",
                request.getRequestURI()));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return respond(ProblemDetails.build(ErrorCode.STALE_VERSION,
                "The resource was changed by someone else. Reload it and try again.", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getClass().getSimpleName());
        if (message.contains("uq_") || message.contains("duplicate key")) {
            return respond(ProblemDetails.build(ErrorCode.DUPLICATE_RESOURCE,
                    "A resource with the same unique value already exists.", request.getRequestURI()));
        }
        return respond(ProblemDetails.build(ErrorCode.DATA_CONFLICT,
                "The request conflicts with existing data.", request.getRequestURI()));
    }

    /** Let Spring Security's ExceptionTranslationFilter decide between 401 (anonymous) and 403 (authenticated). */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ProblemDetails.build(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the request id when reporting it.", request.getRequestURI()));
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String safeMessage(String message, String code) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return code == null ? "is invalid" : "is invalid (" + code + ")";
    }

    private static String lastNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
