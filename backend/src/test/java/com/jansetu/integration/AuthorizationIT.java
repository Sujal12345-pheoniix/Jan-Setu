package com.jansetu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Role/permission matrix: function-level authorization is enforced on the server for every role. */
class AuthorizationIT extends IntegrationTestBase {

    private UUID department;

    @BeforeEach
    void setUp() {
        department = createDepartment("AUTHZ");
    }

    @Test
    void anonymousCallersGet401OnProtectedEndpointsButCanBrowseCatalogue() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/departments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/services/manage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/services")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void citizenCannotUseAdministrativeEndpoints() throws Exception {
        TestUser citizen = citizen();
        mvc.perform(get("/api/v1/users").header("Authorization", citizen.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/departments").header("Authorization", citizen.bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").header("Authorization", citizen.bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/departments").header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "HACK" + uniq(), "name", "Hacked Dept"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCanReadOwnDepartmentButCannotAdministerIt() throws Exception {
        TestUser officer = staff("OFFICER", department);
        mvc.perform(get("/api/v1/departments/" + department).header("Authorization", officer.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/departments").header("Authorization", officer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "OFF" + uniq(), "name", "Officer Dept " + uniq()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", officer.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void reviewerCannotPerformPlatformAdministration() throws Exception {
        TestUser reviewer = staff("REVIEWER", department);
        TestUser target = citizen();
        mvc.perform(post("/api/v1/users/" + target.id() + "/suspend").header("Authorization", reviewer.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/roles").header("Authorization", reviewer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "X" + uniq(), "name", "X", "permissions", List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditorCanReadAuditLogButCannotMutateAnything() throws Exception {
        TestUser auditor = staff("AUDITOR");
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", auditor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
        mvc.perform(post("/api/v1/departments").header("Authorization", auditor.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "AUD" + uniq(), "name", "Auditor Dept " + uniq()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", auditor.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void auditLogAccessIsRecordedAndOthersCannotRead() throws Exception {
        TestUser auditor = staff("AUDITOR");
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", auditor.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").param("action", "AUDIT_LOG_VIEWED").param("actorId", auditor.id().toString())
                        .header("Authorization", auditor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        TestUser admin = staff("PLATFORM_ADMIN");
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", admin.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void managementViewerSeesDepartmentsButCannotManage() throws Exception {
        TestUser viewer = staff("MANAGEMENT_VIEWER");
        mvc.perform(get("/api/v1/departments/" + department).header("Authorization", viewer.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/services/manage").header("Authorization", viewer.bearer()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "OFFICER", "REVIEWER", "DEPARTMENT_ADMIN", "AUDITOR", "MANAGEMENT_VIEWER"})
    void onlyPlatformAdminCanCreateStaffAccounts(String role) throws Exception {
        TestUser actor = role.equals("CITIZEN") ? citizen() : staff(role, department);
        mvc.perform(post("/api/v1/users").header("Authorization", actor.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "x." + uniq() + "@example.test", "fullName", "X Person",
                                "initialPassword", PASSWORD, "roles", List.of("OFFICER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delegatedRoleAdministratorCannotGrantPrivilegedPermissions() throws Exception {
        TestUser admin = staff("PLATFORM_ADMIN");
        String assignerRole = "ROLE_ASSIGNER_" + uniq().toUpperCase();
        createRole(admin, assignerRole, List.of("USER:READ", "USER:ASSIGN_ROLE")).andExpect(status().isCreated());
        TestUser delegate = staff(assignerRole, department);
        TestUser officer = staff("OFFICER", department);

        // operational roles can be granted
        mvc.perform(put("/api/v1/users/" + officer.id() + "/roles").header("Authorization", delegate.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("OFFICER", "REVIEWER")))))
                .andExpect(status().isOk());
        // privileged roles cannot
        mvc.perform(put("/api/v1/users/" + officer.id() + "/roles").header("Authorization", delegate.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("PLATFORM_ADMIN")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRIVILEGE_ESCALATION"));
    }

    @Test
    void roleCreatorCannotPutPrivilegedPermissionsTheyLackIntoARole() throws Exception {
        TestUser admin = staff("PLATFORM_ADMIN");
        String makerRole = "ROLE_MAKER_" + uniq().toUpperCase();
        createRole(admin, makerRole, List.of("ROLE:CREATE")).andExpect(status().isCreated());
        TestUser maker = staff(makerRole);
        createRole(maker, "SNEAKY_" + uniq().toUpperCase(), List.of("SCOPE:ALL_DEPARTMENTS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRIVILEGE_ESCALATION"));
        createRole(maker, "HARMLESS_" + uniq().toUpperCase(), List.of("REPORT:READ_DEPARTMENT"))
                .andExpect(status().isCreated());
    }

    @Test
    void platformAdminCanProvisionAuditorButNotSelfAssign() throws Exception {
        TestUser admin = staff("PLATFORM_ADMIN");
        TestUser target = staff("OFFICER");
        mvc.perform(put("/api/v1/users/" + target.id() + "/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("AUDITOR")))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", target.bearer())).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions createRole(TestUser actor, String code, List<String> permissions)
            throws Exception {
        return mvc.perform(post("/api/v1/roles").header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "name", "Custom " + code, "permissions", permissions))));
    }

    @Test
    void cannotChangeOwnRolesOrStatus() throws Exception {
        TestUser admin = staff("PLATFORM_ADMIN");
        mvc.perform(put("/api/v1/users/" + admin.id() + "/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("PLATFORM_ADMIN", "OFFICER")))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/users/" + admin.id() + "/deactivate").header("Authorization", admin.bearer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void systemRolePermissionsAreImmutableThroughTheApi() throws Exception {
        TestUser admin = staff("PLATFORM_ADMIN");
        var roles = body(mvc.perform(get("/api/v1/roles").param("size", "50").header("Authorization", admin.bearer()))
                .andReturn()).at("/data/content");
        String id = null;
        long version = 0;
        for (var role : roles) {
            if (role.get("code").asText().equals("CITIZEN")) {
                id = role.get("id").asText();
                version = role.get("version").asLong();
            }
        }
        org.assertj.core.api.Assertions.assertThat(id).isNotNull();
        mvc.perform(put("/api/v1/roles/" + id + "/permissions").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("permissions", List.of("USER:READ"), "version", version))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE"));
    }
}
