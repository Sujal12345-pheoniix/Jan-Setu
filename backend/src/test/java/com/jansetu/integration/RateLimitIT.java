package com.jansetu.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "jansetu.security.rate-limit.auth-requests-per-minute=3")
class RateLimitIT extends IntegrationTestBase {

    @Test
    void credentialEndpointsAreRateLimitedPerClient() throws Exception {
        String body = json(Map.of("email", "rate." + uniq() + "@example.test"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(r -> { r.setRemoteAddr("10.9.8.7"); return r; }))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(r -> { r.setRemoteAddr("10.9.8.7"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
        // another client is unaffected
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(r -> { r.setRemoteAddr("10.9.8.8"); return r; }))
                .andExpect(status().isAccepted());
    }
}
