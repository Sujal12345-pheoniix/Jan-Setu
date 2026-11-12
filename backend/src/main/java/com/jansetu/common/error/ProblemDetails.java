package com.jansetu.common.error;

import com.jansetu.common.web.RequestIds;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details with JanSetu's extension members. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public record FieldError(String field, String message) {
    }

    public static ProblemDetail build(ErrorCode code, String detail, String path) {
        return build(code, code.status(), detail, path, null);
    }

    public static ProblemDetail build(ErrorCode code, HttpStatusCode status, String detail, String path,
                                      List<FieldError> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:jansetu:problem:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(code.title());
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        problem.setProperty("errorCode", code.name());
        problem.setProperty("requestId", RequestIds.current());
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return problem;
    }
}
