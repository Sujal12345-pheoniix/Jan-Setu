package com.jansetu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Problem-details contract, correlation ids and safe error output. */
class ErrorHandlingIT extends IntegrationTestBase {

    @Test
    void malformedJsonReturnsProblemDetailsWithRequestId() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:jansetu:problem:malformed-request"))
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id"))
                .isEqualTo(body(result).get("requestId").asText());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("at com.");
    }

    @Test
    void wellFormedIncomingRequestIdIsPropagatedAndUnsafeOneReplaced() throws Exception {
        mvc.perform(get("/api/v1/services").header("X-Request-Id", "trace-abc-12345"))
                .andExpect(header().string("X-Request-Id", "trace-abc-12345"))
                .andExpect(jsonPath("$.meta.requestId").value("trace-abc-12345"));
        MvcResult unsafe = mvc.perform(get("/api/v1/services").header("X-Request-Id", "bad\nid<script>")).andReturn();
        assertThat(unsafe.getResponse().getHeader("X-Request-Id")).doesNotContain("script").hasSize(36);
    }

    @Test
    void unknownEndpointAndWrongMethodAndMediaType() throws Exception {
        TestUser user = citizen();
        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", user.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ENDPOINT_NOT_FOUND"));
        mvc.perform(delete("/api/v1/auth/me").header("Authorization", user.bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/services"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiAndHealthAreAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/auth/login")))
                .andExpect(content().string(containsString("bearerAuth")));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
