package com.jansetu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jansetu.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends IntegrationTestBase {

    private static final String CLIENT_HEADER = "X-JanSetu-Client";

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        void registersCitizenWithCitizenRoleAndNeverReturnsPassword() throws Exception {
            String email = "New.User." + uniq() + "@Example.test";
            MvcResult result = mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", PASSWORD, "fullName", "Asha Demo"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.userType").value("CITIZEN"))
                    .andExpect(jsonPath("$.data.roles[0]").value("CITIZEN"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                    .andReturn();
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(PASSWORD)
                    .doesNotContainIgnoringCase("passwordHash")
                    .doesNotContainIgnoringCase("$2a$");
            // e-mail is case-insensitive for login
            login(email.toLowerCase(), PASSWORD);
        }

        @Test
        void rejectsDuplicateEmailCaseInsensitively() throws Exception {
            String email = "dup." + uniq() + "@example.test";
            register(email).andExpect(status().isCreated());
            register(email.toUpperCase())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
        }

        @Test
        void rejectsWeakPasswordAndInvalidFields() throws Exception {
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "weak." + uniq() + "@example.test", "password", "short1",
                                    "fullName", "Weak"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "not-an-email", "password", PASSWORD, "fullName", ""))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'fullName')]").exists());
        }

        @Test
        void rejectsMassAssignmentOfUnknownFields() throws Exception {
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "mass." + uniq() + "@example.test", "password", PASSWORD,
                                    "fullName", "Mass Assign", "roles", java.util.List.of("PLATFORM_ADMIN")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
        }

        private org.springframework.test.web.servlet.ResultActions register(String email) throws Exception {
            return mvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", email, "password", PASSWORD, "fullName", "Dup User"))));
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void returnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
            TestUser user = citizen();
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", user.email(), "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(900))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(cookie().httpOnly("jansetu_rt", true))
                    .andExpect(cookie().path("jansetu_rt", "/api/v1/auth"))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }

        @Test
        void usesTheSameGenericErrorForUnknownAccountAndWrongPassword() throws Exception {
            TestUser user = citizen();
            String unknown = mvc.perform(loginRequest("nobody." + uniq() + "@example.test", PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            String wrong = mvc.perform(loginRequest(user.email(), "Wrong-Password-123"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            JsonNode a = objectMapper.readTree(unknown);
            JsonNode b = objectMapper.readTree(wrong);
            assertThat(a.get("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(b.get("errorCode").asText());
            assertThat(a.get("detail")).isEqualTo(b.get("detail"));
        }

        @Test
        void locksAccountAfterFiveFailedAttemptsEvenForTheCorrectPassword() throws Exception {
            TestUser user = citizen();
            for (int i = 0; i < 5; i++) {
                mvc.perform(loginRequest(user.email(), "Wrong-Password-" + i)).andExpect(status().isUnauthorized());
            }
            mvc.perform(loginRequest(user.email(), PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        void suspendedAccountCannotLoginAndExistingTokenStopsWorking() throws Exception {
            TestUser admin = staff("PLATFORM_ADMIN");
            TestUser user = citizen();
            mvc.perform(get("/api/v1/auth/me").header("Authorization", user.bearer())).andExpect(status().isOk());

            mvc.perform(post("/api/v1/users/" + user.id() + "/suspend").header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "test"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

            mvc.perform(get("/api/v1/auth/me").header("Authorization", user.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
            mvc.perform(loginRequest(user.email(), PASSWORD))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
            mvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()).header(CLIENT_HEADER, "web"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Tokens")
    class Tokens {

        @Test
        void meRequiresAuthentication() throws Exception {
            mvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        }

        @Test
        void rejectsTamperedAndForeignTokens() throws Exception {
            TestUser user = citizen();
            String[] parts = user.accessToken().split("\\.");
            String tamperedPayload = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tamperedPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

            String noneHeader = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String unsigned = noneHeader + "." + parts[1] + ".";
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + unsigned))
                    .andExpect(status().isUnauthorized());

            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void meReturnsRolesPermissionsAndDepartments() throws Exception {
            var department = createDepartment("ME");
            TestUser officer = staff("OFFICER", department);
            mvc.perform(get("/api/v1/auth/me").header("Authorization", officer.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.roles[0]").value("OFFICER"))
                    .andExpect(jsonPath("$.data.user.departmentIds[0]").value(department.toString()))
                    .andExpect(jsonPath("$.data.user.passwordChangeRequired").value(true))
                    .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.hasItem("TASK:CLAIM")))
                    .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Refresh rotation")
    class Refresh {

        @Test
        void rotatesRefreshTokenAndIssuesNewAccessToken() throws Exception {
            TestUser user = citizen();
            MvcResult result = refresh(user.refreshCookie()).andExpect(status().isOk()).andReturn();
            Cookie rotated = result.getResponse().getCookie("jansetu_rt");
            assertThat(rotated).isNotNull();
            assertThat(rotated.getValue()).isNotEqualTo(user.refreshCookie().getValue());
            String newAccess = body(result).at("/data/accessToken").asText();
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newAccess)).andExpect(status().isOk());
            refresh(rotated).andExpect(status().isOk());
        }

        @Test
        void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
            TestUser user = citizen();
            Cookie original = user.refreshCookie();
            Cookie rotated = refresh(original).andExpect(status().isOk()).andReturn().getResponse().getCookie("jansetu_rt");

            // Attacker replays the old token -> rejected and the family is revoked
            refresh(original)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
            // The legitimate, newer token is now revoked as well
            refresh(rotated).andExpect(status().isUnauthorized());
        }

        @Test
        void requiresClientHeaderAsCsrfDefence() throws Exception {
            TestUser user = citizen();
            mvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("CLIENT_HEADER_REQUIRED"));
        }

        @Test
        void rejectsMissingOrUnknownRefreshToken() throws Exception {
            mvc.perform(post("/api/v1/auth/refresh").header(CLIENT_HEADER, "web"))
                    .andExpect(status().isUnauthorized());
            refresh(new Cookie("jansetu_rt", "made-up-token")).andExpect(status().isUnauthorized());
        }

        private org.springframework.test.web.servlet.ResultActions refresh(Cookie cookie) throws Exception {
            return mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header(CLIENT_HEADER, "web"));
        }
    }

    @Nested
    @DisplayName("Logout and sessions")
    class Logout {

        @Test
        void logoutRevokesRefreshSessionAndClearsCookie() throws Exception {
            TestUser user = citizen();
            mvc.perform(post("/api/v1/auth/logout").cookie(user.refreshCookie()).header(CLIENT_HEADER, "web"))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge("jansetu_rt", 0));
            mvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()).header(CLIENT_HEADER, "web"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void listsAndRevokesDeviceSessions() throws Exception {
            TestUser first = citizen();
            TestUser second = login(first.email(), PASSWORD);
            MvcResult sessions = mvc.perform(get("/api/v1/auth/sessions")
                            .header("Authorization", first.bearer()).cookie(first.refreshCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andReturn();
            String otherSessionId = null;
            for (JsonNode s : body(sessions).at("/data")) {
                if (!s.get("current").asBoolean()) {
                    otherSessionId = s.get("id").asText();
                }
            }
            assertThat(otherSessionId).isNotNull();
            mvc.perform(delete("/api/v1/auth/sessions/" + otherSessionId).header("Authorization", first.bearer()))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/v1/auth/refresh").cookie(second.refreshCookie()).header(CLIENT_HEADER, "web"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void cannotRevokeAnotherUsersSession() throws Exception {
            TestUser owner = citizen();
            TestUser other = citizen();
            String ownerSession = body(mvc.perform(get("/api/v1/auth/sessions").header("Authorization", owner.bearer()))
                    .andReturn()).at("/data/0/id").asText();
            mvc.perform(delete("/api/v1/auth/sessions/" + ownerSession).header("Authorization", other.bearer()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Password reset and change")
    class PasswordReset {

        @Test
        void fullResetFlowIsSingleUseAndRevokesSessions() throws Exception {
            TestUser user = citizen();
            mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", user.email()))))
                    .andExpect(status().isAccepted());
            String token = emails.lastResetTokenFor(user.email()).orElseThrow();
            String newPassword = "Brand-New-Passphrase-77";

            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", token, "newPassword", newPassword))))
                    .andExpect(status().isOk());
            // single use
            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", token, "newPassword", "Another-Passphrase-88"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_RESET_TOKEN"));
            // old sessions are revoked, old password no longer works, new one does
            mvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie()).header(CLIENT_HEADER, "web"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(loginRequest(user.email(), PASSWORD)).andExpect(status().isUnauthorized());
            login(user.email(), newPassword);
        }

        @Test
        void forgotPasswordDoesNotRevealWhetherAccountExists() throws Exception {
            String unknown = "ghost." + uniq() + "@example.test";
            String a = mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", unknown))))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(a).at("/data/message").asText()).contains("If an active account exists");
            assertThat(emails.sentTo(unknown)).isEmpty();
        }

        @Test
        void changePasswordInvalidatesOlderAccessTokens() throws Exception {
            TestUser user = citizen();
            Thread.sleep(1100); // access token "iat" has second precision
            mvc.perform(post("/api/v1/auth/change-password").header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "Changed-Passphrase-99"))))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/auth/me").header("Authorization", user.bearer()))
                    .andExpect(status().isUnauthorized());
            TestUser again = login(user.email(), "Changed-Passphrase-99");
            mvc.perform(get("/api/v1/auth/me").header("Authorization", again.bearer())).andExpect(status().isOk());
        }

        @Test
        void changePasswordRequiresCorrectCurrentPassword() throws Exception {
            TestUser user = citizen();
            mvc.perform(post("/api/v1/auth/change-password").header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("currentPassword", "Not-The-Password-1", "newPassword", "Changed-Passphrase-99"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", not(containsString("hash"))));
        }
    }

    private org.springframework.test.web.servlet.RequestBuilder loginRequest(String email, String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }
}
