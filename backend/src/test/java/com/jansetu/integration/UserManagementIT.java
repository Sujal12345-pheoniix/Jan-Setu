package com.jansetu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class UserManagementIT extends IntegrationTestBase {

    private TestUser admin;

    @BeforeEach
    void setUp() {
        admin = staff("PLATFORM_ADMIN");
    }

    @Test
    void createsStaffUserWhoMustChangePasswordAndCanLogIn() throws Exception {
        String email = "officer." + uniq() + "@example.test";
        mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "fullName", "New Officer", "initialPassword", PASSWORD,
                                "roles", List.of("OFFICER")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userType").value("STAFF"))
                .andExpect(jsonPath("$.data.roles[0]").value("OFFICER"))
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.data.initialPassword").doesNotExist());
        login(email, PASSWORD);
    }

    @Test
    void rejectsInvalidStaffRequests() throws Exception {
        // unknown role
        mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "x." + uniq() + "@example.test", "fullName", "X Person",
                                "initialPassword", PASSWORD, "roles", List.of("SUPER_USER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        // staff cannot be citizen
        mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "y." + uniq() + "@example.test", "fullName", "Y Person",
                                "initialPassword", PASSWORD, "roles", List.of("CITIZEN")))))
                .andExpect(status().isUnprocessableEntity());
        // missing roles
        mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "z." + uniq() + "@example.test", "fullName", "Z Person",
                                "initialPassword", PASSWORD, "roles", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("roles"));
    }

    @Test
    void idempotencyKeyPreventsDuplicateCreation() throws Exception {
        String key = "create-user-" + uniq();
        Map<String, Object> request = Map.of("email", "idem." + uniq() + "@example.test", "fullName", "Idem Person",
                "initialPassword", PASSWORD, "roles", List.of("OFFICER"));
        String first = body(mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();
        mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(first));
    }

    @Test
    void updatesProfileWithOptimisticLocking() throws Exception {
        TestUser user = citizen();
        long version = body(mvc.perform(get("/api/v1/users/" + user.id()).header("Authorization", admin.bearer()))
                .andReturn()).at("/data/version").asLong();
        mvc.perform(patch("/api/v1/users/" + user.id()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Renamed Citizen", "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Renamed Citizen"))
                .andExpect(jsonPath("$.data.version").value(version + 1));
        // stale version
        mvc.perform(patch("/api/v1/users/" + user.id()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Second Rename", "version", version))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_VERSION"));
    }

    @Test
    void citizenCanUpdateOwnProfileOnly() throws Exception {
        TestUser user = citizen();
        long version = body(mvc.perform(get("/api/v1/auth/me").header("Authorization", user.bearer()))
                .andReturn()).at("/data/user/version").asLong();
        mvc.perform(patch("/api/v1/auth/me").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "+91 98765 43210", "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+91 98765 43210"));
        TestUser other = citizen();
        mvc.perform(patch("/api/v1/users/" + other.id()).header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Hijack", "version", 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusLifecycleAndReactivation() throws Exception {
        TestUser user = citizen();
        mvc.perform(post("/api/v1/users/" + user.id() + "/deactivate").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEACTIVATED"));
        mvc.perform(post("/api/v1/users/" + user.id() + "/deactivate").header("Authorization", admin.bearer()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/users/" + user.id() + "/reactivate").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        login(user.email(), PASSWORD);
    }

    @Test
    void replacesStaffRolesAndEnforcesUserTypeRules() throws Exception {
        TestUser officer = staff("OFFICER");
        mvc.perform(put("/api/v1/users/" + officer.id() + "/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("OFFICER", "REVIEWER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2));
        // takes effect on the next request without re-login
        mvc.perform(get("/api/v1/auth/me").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.hasItem("APPLICATION:APPROVE")));

        TestUser citizen = citizen();
        mvc.perform(put("/api/v1/users/" + citizen.id() + "/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("OFFICER")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listsUsersWithFiltersPaginationAndSortAllowlist() throws Exception {
        TestUser user = citizen();
        mvc.perform(get("/api/v1/users").param("q", user.email()).param("userType", "CITIZEN")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value(user.email()));
        mvc.perform(get("/api/v1/users").param("size", "5").param("sort", "email,asc")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(5));
        mvc.perform(get("/api/v1/users").param("sort", "passwordHash,asc").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/v1/users").param("size", "1000").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }
}
