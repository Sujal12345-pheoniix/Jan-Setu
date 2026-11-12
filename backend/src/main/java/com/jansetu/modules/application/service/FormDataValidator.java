package com.jansetu.modules.application.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates application answers against the form schema of the service version the application belongs to.
 * Drafts are validated for types only; submission additionally enforces required fields and declarations.
 * Declarations are stored under the reserved key {@code _declarations} as a list of accepted requirement codes.
 */
@Component
public class FormDataValidator {

    public static final String DECLARATIONS = "_declarations";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 -]{6,18}$");

    public record FieldProblem(String field, String message) {
    }

    @SuppressWarnings("unchecked")
    public void validate(Map<String, Object> formSchema, List<Map<String, Object>> requirements,
                         Map<String, Object> data, boolean forSubmission) {
        List<FieldProblem> problems = new ArrayList<>();
        List<Map<String, Object>> fields = formSchema.get("fields") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        Set<String> known = new HashSet<>();
        for (Map<String, Object> field : fields) {
            String key = String.valueOf(field.get("key"));
            known.add(key);
            Object value = data.get(key);
            boolean required = Boolean.TRUE.equals(field.get("required"));
            if (isBlank(value)) {
                if (forSubmission && required) {
                    problems.add(new FieldProblem(key, "is required"));
                }
                continue;
            }
            String problem = checkType(String.valueOf(field.get("type")), value, field);
            if (problem != null) {
                problems.add(new FieldProblem(key, problem));
            }
        }
        for (String key : data.keySet()) {
            if (!known.contains(key) && !DECLARATIONS.equals(key)) {
                problems.add(new FieldProblem(key, "is not a field of this form"));
            }
        }
        Object declared = data.get(DECLARATIONS);
        if (declared != null && !(declared instanceof List<?>)) {
            problems.add(new FieldProblem(DECLARATIONS, "must be a list of requirement codes"));
        }
        if (forSubmission) {
            List<?> accepted = declared instanceof List<?> list ? list : List.of();
            for (Map<String, Object> requirement : requirements) {
                if ("DECLARATION".equals(requirement.get("requirementType"))
                        && Boolean.TRUE.equals(requirement.get("mandatory"))
                        && !accepted.contains(requirement.get("code"))) {
                    problems.add(new FieldProblem(DECLARATIONS, "Declaration " + requirement.get("code") + " must be accepted"));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new FormValidationException(problems);
        }
    }

    private static String checkType(String type, Object value, Map<String, Object> field) {
        String text = String.valueOf(value);
        return switch (type) {
            case "text" -> value instanceof String && text.length() <= maxLength(field, 500) ? null : "must be text up to " + maxLength(field, 500) + " characters";
            case "textarea" -> value instanceof String && text.length() <= maxLength(field, 4000) ? null : "must be text up to " + maxLength(field, 4000) + " characters";
            case "number" -> isNumber(value) ? null : "must be a number";
            case "date" -> isDate(text) ? null : "must be a date (YYYY-MM-DD)";
            case "email" -> value instanceof String && EMAIL.matcher(text).matches() ? null : "must be a valid e-mail address";
            case "phone" -> value instanceof String && PHONE.matcher(text).matches() ? null : "must be a valid phone number";
            case "checkbox" -> value instanceof Boolean ? null : "must be true or false";
            case "select" -> field.get("options") instanceof List<?> options && options.contains(value) ? null : "must be one of the listed options";
            default -> "has an unsupported type";
        };
    }

    private static int maxLength(Map<String, Object> field, int fallback) {
        return field.get("maxLength") instanceof Number n ? Math.min(n.intValue(), fallback) : fallback;
    }

    private static boolean isNumber(Object value) {
        if (value instanceof Number) {
            return true;
        }
        try {
            new java.math.BigDecimal(String.valueOf(value));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    /** Carries field-level problems; mapped to a 400 VALIDATION_ERROR with fieldErrors. */
    public static class FormValidationException extends ApiException
            implements com.jansetu.common.error.GlobalExceptionHandler.FieldErrorsProvider {
        private final transient List<FieldProblem> problems;

        public FormValidationException(List<FieldProblem> problems) {
            super(ErrorCode.VALIDATION_ERROR, "The application form has invalid or missing answers.");
            this.problems = List.copyOf(problems);
        }

        @Override
        public List<com.jansetu.common.error.ProblemDetails.FieldError> fieldErrors() {
            return problems.stream()
                    .map(p -> new com.jansetu.common.error.ProblemDetails.FieldError("formData." + p.field(), p.message()))
                    .toList();
        }
    }
}
