package com.jansetu.integration;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Department-level data isolation: a department admin of A can neither see nor change anything that belongs to
 * department B. Out-of-scope resources answer 404 so their existence is not revealed.
 */
class DepartmentIsolationIT extends IntegrationTestBase {

    private UUID deptA;
    private UUID deptB;
    private TestUser adminA;
    private TestUser officerB;

    @BeforeEach
    void setUp() {
        deptA = createDepartment("ISO_A");
        deptB = createDepartment("ISO_B");
        adminA = staff("DEPARTMENT_ADMIN", deptA);
        officerB = staff("OFFICER", deptB);
    }

    @Test
    void departmentListContainsOnlyOwnDepartments() throws Exception {
        mvc.perform(get("/api/v1/departments").param("size", "100").header("Authorization", adminA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(deptA.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(deptB.toString()))))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void cannotReadOrUpdateAnotherDepartment() throws Exception {
        mvc.perform(get("/api/v1/departments/" + deptB).header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/departments/" + deptB).header("Authorization", adminA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Taken Over " + uniq(), "version", 0))))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/departments/" + deptA).header("Authorization", adminA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("description", "Updated by own admin", "version", 0))))
                .andExpect(status().isOk());
    }

    @Test
    void cannotManageMembersOfAnotherDepartment() throws Exception {
        TestUser officerA = staff("OFFICER", deptA);
        mvc.perform(post("/api/v1/departments/" + deptB + "/members").header("Authorization", adminA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", officerA.id().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/departments/" + deptB + "/members/" + officerB.id())
                        .header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/departments/" + deptB + "/members").header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void canManageMembersOfOwnDepartmentButOnlyActiveStaff() throws Exception {
        TestUser newcomer = staff("OFFICER");
        mvc.perform(post("/api/v1/departments/" + deptA + "/members").header("Authorization", adminA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", newcomer.id().toString(), "position", "Clerk"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(newcomer.email()));
        // the newcomer immediately gains department scope (authorities are resolved per request)
        mvc.perform(get("/api/v1/departments/" + deptA).header("Authorization", newcomer.bearer()))
                .andExpect(status().isOk());

        TestUser citizen = citizen();
        mvc.perform(post("/api/v1/departments/" + deptA + "/members").header("Authorization", adminA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", citizen.id().toString()))))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(delete("/api/v1/departments/" + deptA + "/members/" + newcomer.id())
                        .header("Authorization", adminA.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/departments/" + deptA).header("Authorization", newcomer.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userAdministrationIsScopedToOwnDepartmentStaff() throws Exception {
        TestUser officerA = staff("OFFICER", deptA);
        mvc.perform(get("/api/v1/users").param("size", "100").header("Authorization", adminA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(officerA.id().toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(officerB.id().toString()))))
                .andExpect(jsonPath("$.data.content[*].departmentIds", everyItem(hasItem(deptA.toString()))));
        mvc.perform(get("/api/v1/users/" + officerB.id()).header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users").param("departmentId", deptB.toString()).header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotCreateOrManageServicesOfAnotherDepartment() throws Exception {
        mvc.perform(post("/api/v1/services").header("Authorization", adminA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(serviceBody(deptB))))
                .andExpect(status().isNotFound());

        TestUser adminB = staff("DEPARTMENT_ADMIN", deptB);
        String serviceB = body(mvc.perform(post("/api/v1/services").header("Authorization", adminB.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(serviceBody(deptB))))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();

        mvc.perform(get("/api/v1/services/" + serviceB + "/manage").header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/services/" + serviceB + "/publish").header("Authorization", adminA.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/services/manage").param("size", "100").header("Authorization", adminA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(serviceB))));
    }

    @Test
    void platformAdminHasGlobalScope() throws Exception {
        TestUser platform = staff("PLATFORM_ADMIN");
        mvc.perform(get("/api/v1/departments/" + deptA).header("Authorization", platform.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/departments/" + deptB).header("Authorization", platform.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/" + officerB.id()).header("Authorization", platform.bearer())).andExpect(status().isOk());
    }

    private Map<String, Object> serviceBody(UUID department) {
        return Map.of("departmentId", department.toString(), "code", "SVC_" + uniq(), "name", "Isolation Service",
                "description", "Service for isolation tests", "category", "CERTIFICATE", "processingDays", 7);
    }
}
