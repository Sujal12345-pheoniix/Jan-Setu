package com.jansetu.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jansetu.common.error.ApiException;
import com.jansetu.modules.catalogue.service.FormSchemaValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormSchemaValidatorTest {

    private final FormSchemaValidator validator = new FormSchemaValidator();

    @Test
    void acceptsValidSchema() {
        assertThatCode(() -> validator.validate(Map.of("fields", List.of(
                Map.of("key", "name", "label", "Name", "type", "text", "required", true),
                Map.of("key", "kind", "label", "Kind", "type", "select", "options", List.of("A", "B"))))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(Map.of())).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateKeysUnknownTypesAndPropertiesAndSelectWithoutOptions() {
        assertInvalid(Map.of("fields", List.of(field("a", "text"), field("a", "number"))), "Duplicate field key");
        assertInvalid(Map.of("fields", List.of(field("a", "html"))), "type must be one of");
        assertInvalid(Map.of("fields", List.of(field("a", "select"))), "options");
        assertInvalid(Map.of("fields", List.of(Map.of("key", "a", "label", "A", "type", "text", "onclick", "x"))),
                "unsupported property");
        assertInvalid(Map.of("script", "alert(1)"), "Unsupported form schema property");
        assertInvalid(Map.of("fields", List.of(field("1bad", "text"))), "key must start with a letter");
        assertInvalid(Map.of("fields", "not-an-array"), "must be an array");
    }

    private static Map<String, Object> field(String key, String type) {
        return Map.of("key", key, "label", "Label " + key, "type", type);
    }

    private void assertInvalid(Map<String, Object> schema, String message) {
        assertThatThrownBy(() -> validator.validate(schema)).isInstanceOf(ApiException.class).hasMessageContaining(message);
    }
}
