package com.jansetu.modules.catalogue.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates the service form definition. Supported shape:
 *
 * <pre>{"fields": [{"key": "annualIncome", "label": "Annual income", "type": "number", "required": true}, ...]}</pre>
 *
 * Supported types: text, textarea, number, date, email, phone, checkbox, select (select requires "options").
 */
@Component
public class FormSchemaValidator {

    static final Set<String> TYPES = Set.of("text", "textarea", "number", "date", "email", "phone", "checkbox", "select");
    private static final Set<String> FIELD_KEYS = Set.of("key", "label", "type", "required", "options", "helpText",
            "maxLength", "min", "max");
    private static final Pattern KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,49}$");
    private static final int MAX_FIELDS = 50;

    public void validate(Map<String, Object> schema) {
        if (schema == null) {
            return;
        }
        for (String key : schema.keySet()) {
            if (!key.equals("fields")) {
                fail("Unsupported form schema property '" + key + "'. Only 'fields' is allowed.");
            }
        }
        Object fieldsValue = schema.get("fields");
        if (fieldsValue == null) {
            return;
        }
        if (!(fieldsValue instanceof List<?> fields)) {
            fail("'fields' must be an array.");
            return;
        }
        if (fields.size() > MAX_FIELDS) {
            fail("A form can have at most " + MAX_FIELDS + " fields.");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            if (!(fields.get(i) instanceof Map<?, ?> field)) {
                fail("fields[" + i + "] must be an object.");
                return;
            }
            for (Object property : field.keySet()) {
                if (!FIELD_KEYS.contains(String.valueOf(property))) {
                    fail("fields[" + i + "] has unsupported property '" + property + "'.");
                }
            }
            Object key = field.get("key");
            if (!(key instanceof String k) || !KEY.matcher(k).matches()) {
                fail("fields[" + i + "].key must start with a letter and contain only letters, digits or '_'.");
                return;
            }
            if (!seen.add(k)) {
                fail("Duplicate field key '" + k + "'.");
            }
            if (!(field.get("label") instanceof String label) || label.isBlank() || label.length() > 200) {
                fail("fields[" + i + "].label is required (max 200 characters).");
            }
            Object type = field.get("type");
            if (!(type instanceof String t) || !TYPES.contains(t)) {
                fail("fields[" + i + "].type must be one of " + String.join(", ", TYPES.stream().sorted().toList()) + ".");
                return;
            }
            if (field.containsKey("required") && !(field.get("required") instanceof Boolean)) {
                fail("fields[" + i + "].required must be a boolean.");
            }
            if (t.equals("select")) {
                if (!(field.get("options") instanceof List<?> options) || options.isEmpty()
                        || !options.stream().allMatch(o -> o instanceof String s && !s.isBlank())) {
                    fail("fields[" + i + "].options must be a non-empty array of strings for select fields.");
                }
            }
        }
    }

    private static void fail(String message) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid form schema: " + message);
    }
}
