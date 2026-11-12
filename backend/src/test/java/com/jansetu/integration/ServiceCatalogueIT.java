package com.jansetu.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jansetu.support.IntegrationTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ServiceCatalogueIT extends IntegrationTestBase {

    private UUID department;
    private TestUser deptAdmin;

    @BeforeEach
    void setUp() {
        department = createDepartment("CAT");
        deptAdmin = staff("DEPARTMENT_ADMIN", department);
    }

    @Test
    void draftIsInvisibleUntilPublishedThenPublicCatalogueShowsSnapshot() throws Exception {
        JsonNode created = createService(Map.of());
        String id = created.get("id").asText();

        mvc.perform(get("/api/v1/services/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/services").param("departmentId", department.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        addRequirement(id, "ID_PROOF", "DOCUMENT").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/services/" + id + "/publish").header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(get("/api/v1/services/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.requirements[0].code").value("ID_PROOF"))
                .andExpect(jsonPath("$.data.formSchema.fields[0].key").value("applicantName"));
        mvc.perform(get("/api/v1/services").param("departmentId", department.toString()))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
    }

    @Test
    void publishedVersionsAreImmutableAndEditsNeedRepublishing() throws Exception {
        JsonNode created = createService(Map.of());
        String id = created.get("id").asText();
        mvc.perform(post("/api/v1/services/" + id + "/publish").header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isOk());
        // nothing changed -> cannot publish again
        mvc.perform(post("/api/v1/services/" + id + "/publish").header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isConflict());

        long version = currentVersion(id);
        mvc.perform(patch("/api/v1/services/" + id).header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Renamed Service", "processingDays", 3, "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));

        // public view still shows version 1 until republished
        mvc.perform(get("/api/v1/services/" + id)).andExpect(jsonPath("$.data.name").value("Test Service"));
        mvc.perform(post("/api/v1/services/" + id + "/publish").header("Authorization", deptAdmin.bearer()))
                .andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get("/api/v1/services/" + id)).andExpect(jsonPath("$.data.name").value("Renamed Service"));
        mvc.perform(get("/api/v1/services/" + id + "/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Service"))
                .andExpect(jsonPath("$.data.processingDays").value(7));
    }

    @Test
    void deprecatedServiceLeavesPublicCatalogueButHistoryRemains() throws Exception {
        String id = createService(Map.of()).get("id").asText();
        mvc.perform(post("/api/v1/services/" + id + "/publish").header("Authorization", deptAdmin.bearer()));
        mvc.perform(post("/api/v1/services/" + id + "/deprecate").header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));
        mvc.perform(get("/api/v1/services/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/services/" + id + "/versions/1")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/services/" + id).header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Edit deprecated", "version", currentVersion(id)))))
                .andExpect(status().isConflict());
    }

    @Test
    void requirementsCanBeAddedUpdatedAndRemoved() throws Exception {
        String id = createService(Map.of()).get("id").asText();
        String reqId = body(addRequirement(id, "address_proof", "DOCUMENT")
                .andExpect(jsonPath("$.data.code").value("ADDRESS_PROOF"))
                .andReturn()).at("/data/id").asText();
        addRequirement(id, "ADDRESS_PROOF", "DOCUMENT").andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/services/" + id + "/requirements/" + reqId).header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("mandatory", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mandatory").value(false));
        mvc.perform(delete("/api/v1/services/" + id + "/requirements/" + reqId).header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/services/" + id + "/manage").header("Authorization", deptAdmin.bearer()))
                .andExpect(jsonPath("$.data.requirements.length()").value(0));
    }

    @Test
    void validatesServiceAndFormSchema() throws Exception {
        Map<String, Object> bad = new HashMap<>(baseService());
        bad.put("processingDays", 0);
        bad.put("feeAmount", "12.345");
        bad.put("category", "has spaces");
        mvc.perform(post("/api/v1/services").header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));

        Map<String, Object> badSchema = new HashMap<>(baseService());
        badSchema.put("formSchema", Map.of("fields", List.of(Map.of("key", "x", "label", "X", "type", "script"))));
        mvc.perform(post("/api/v1/services").header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(badSchema)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Invalid form schema")));
    }

    @Test
    void cannotCreateServiceInInactiveDepartment() throws Exception {
        TestUser platform = staff("PLATFORM_ADMIN");
        mvc.perform(post("/api/v1/departments/" + department + "/deactivate").header("Authorization", platform.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/services").header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(baseService())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createIsIdempotentWithIdempotencyKey() throws Exception {
        String key = "svc-" + uniq();
        Map<String, Object> request = baseService();
        String first = body(mvc.perform(post("/api/v1/services").header("Authorization", deptAdmin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();
        mvc.perform(post("/api/v1/services").header("Authorization", deptAdmin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(first));
        mvc.perform(get("/api/v1/services/manage").param("q", "Test Service").param("size", "100")
                        .header("Authorization", deptAdmin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void publicCatalogueSupportsFiltersAndRejectsUnknownSort() throws Exception {
        String id = createService(Map.of()).get("id").asText();
        mvc.perform(post("/api/v1/services/" + id + "/publish").header("Authorization", deptAdmin.bearer()));
        mvc.perform(get("/api/v1/services").param("category", "certificate").param("q", "test serv")
                        .param("departmentId", department.toString()))
                .andExpect(jsonPath("$.data.content[0].id").value(id));
        mvc.perform(get("/api/v1/services").param("category", "LICENSE").param("departmentId", department.toString()))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(id))));
        mvc.perform(get("/api/v1/services").param("sort", "formSchema")).andExpect(status().isBadRequest());
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private Map<String, Object> baseService() {
        return Map.of("departmentId", department.toString(), "code", "SVC_" + uniq(), "name", "Test Service",
                "description", "A synthetic test service", "category", "CERTIFICATE", "processingDays", 7,
                "feeAmount", "100.50",
                "formSchema", Map.of("fields", List.of(Map.of("key", "applicantName", "label", "Applicant name",
                        "type", "text", "required", true))));
    }

    private JsonNode createService(Map<String, Object> overrides) throws Exception {
        Map<String, Object> request = new HashMap<>(baseService());
        request.putAll(overrides);
        return body(mvc.perform(post("/api/v1/services").header("Authorization", deptAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.feeAmount").value(100.50))
                .andReturn()).get("data");
    }

    private org.springframework.test.web.servlet.ResultActions addRequirement(String serviceId, String code, String type)
            throws Exception {
        return mvc.perform(post("/api/v1/services/" + serviceId + "/requirements").header("Authorization", deptAdmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "name", "Requirement " + code, "requirementType", type,
                        "allowedMediaTypes", "application/pdf", "maxSizeBytes", 1_048_576))));
    }

    private long currentVersion(String serviceId) throws Exception {
        return body(mvc.perform(get("/api/v1/services/" + serviceId + "/manage").header("Authorization", deptAdmin.bearer()))
                .andReturn()).at("/data/version").asLong();
    }
}
