package com.jansetu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DepartmentIT extends IntegrationTestBase {

    private TestUser admin;

    @BeforeEach
    void setUp() {
        admin = staff("PLATFORM_ADMIN");
    }

    @Test
    void createsReadsUpdatesAndDeactivatesDepartment() throws Exception {
        String code = "dept_" + uniq();
        String id = body(mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "Department " + code,
                                "contactEmail", "Desk@Example.test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.data.contactEmail").value("desk@example.test"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn()).at("/data/id").asText();

        mvc.perform(get("/api/v1/departments/" + id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0));

        mvc.perform(patch("/api/v1/departments/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("description", "Updated description", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(post("/api/v1/departments/" + id + "/deactivate").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
        mvc.perform(get("/api/v1/departments").param("status", "INACTIVE").param("q", code)
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void rejectsDuplicateCodeAndName() throws Exception {
        String code = "DUP_" + uniq().toUpperCase();
        Map<String, Object> request = Map.of("code", code, "name", "Duplicate " + code);
        mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
        mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "OTHER_" + uniq(), "name", "duplicate " + code.toLowerCase()))))
                .andExpect(status().isConflict());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        var id = createDepartment("STALE");
        mvc.perform(patch("/api/v1/departments/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "First change " + uniq(), "version", 0))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/departments/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Second change " + uniq(), "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_VERSION"));
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "1 bad code", "name", "x", "contactEmail", "nope"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
        mvc.perform(patch("/api/v1/departments/" + createDepartment("NOVER")).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Missing version"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
    }

    @Test
    void unknownDepartmentIs404AndBadIdIs400() throws Exception {
        mvc.perform(get("/api/v1/departments/" + java.util.UUID.randomUUID()).header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/v1/departments/not-a-uuid").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void createIsIdempotentAndRejectsKeyReuseWithDifferentBody() throws Exception {
        String key = "dept-key-" + uniq();
        String code = "IDEM_" + uniq().toUpperCase();
        String first = body(mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "Idempotent " + code))))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();
        mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "Idempotent " + code))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(first));
        mvc.perform(post("/api/v1/departments").header("Authorization", admin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code + "X", "name", "Different " + code))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
    }
}
