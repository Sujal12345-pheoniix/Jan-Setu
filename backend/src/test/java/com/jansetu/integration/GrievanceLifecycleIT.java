package com.jansetu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jansetu.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class GrievanceLifecycleIT extends IntegrationTestBase {

    private UUID department;
    private TestUser citizen;
    private TestUser officer;
    private TestUser admin;

    @BeforeEach
    void setUp() {
        department = createDepartment("GRV");
        citizen = citizen();
        officer = staff("OFFICER", department);
        admin = staff("DEPARTMENT_ADMIN", department);
    }

    @Test
    void fullLifecycleWithInternalNotesHiddenFromCitizen() throws Exception {
        String id = create().get("summary").get("id").asText();
        mvc.perform(multipart("/api/v1/grievances/" + id + "/documents")
                        .file(new MockMultipartFile("file", "evidence.pdf", "application/pdf", PDF))
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isCreated());

        act(officer, id, "acknowledge", null).andExpect(jsonPath("$.data.summary.status").value("ACKNOWLEDGED"));
        act(admin, id, "assign", Map.of("assigneeId", officer.id().toString()))
                .andExpect(jsonPath("$.data.summary.status").value("ASSIGNED"));
        act(officer, id, "start", null).andExpect(jsonPath("$.data.summary.status").value("IN_PROGRESS"));
        act(officer, id, "comments", Map.of("visibility", "INTERNAL", "body", "Checked counter logs; delay confirmed."))
                .andExpect(status().isCreated());
        act(officer, id, "comments", Map.of("visibility", "PUBLIC", "body", "We are looking into the delay."))
                .andExpect(status().isCreated());
        act(citizen, id, "comments", Map.of("visibility", "INTERNAL", "body", "sneaky")).andExpect(status().isForbidden());
        act(citizen, id, "comments", Map.of("visibility", "PUBLIC", "body", "Thank you.")).andExpect(status().isCreated());

        JsonNode citizenView = body(mvc.perform(get("/api/v1/grievances/" + id).header("Authorization", citizen.bearer()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(citizenView.get("comments").findValuesAsText("visibility")).containsOnly("PUBLIC");
        assertThat(citizenView.toString()).doesNotContain("counter logs");
        assertThat(citizenView.get("documents")).hasSize(1);
        JsonNode staffView = body(mvc.perform(get("/api/v1/grievances/" + id).header("Authorization", officer.bearer()))
                .andReturn()).get("data");
        assertThat(staffView.toString()).contains("counter logs");

        act(officer, id, "resolve", Map.of("text", "Your certificate has now been issued.")).andExpect(
                jsonPath("$.data.summary.status").value("RESOLVED"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.content[*].type", hasItem("GRIEVANCE_RESOLVED")));

        // citizen may reopen once within the window
        act(citizen, id, "reopen", Map.of("text", "The certificate has a spelling mistake.")).andExpect(
                jsonPath("$.data.summary.status").value("REOPENED"));
        act(officer, id, "start", null).andExpect(status().isOk());
        act(officer, id, "resolve", Map.of("text", "Corrected certificate issued.")).andExpect(status().isOk());
        act(citizen, id, "reopen", Map.of("text", "Again?")).andExpect(status().isUnprocessableEntity());

        act(citizen, id, "close", null).andExpect(jsonPath("$.data.summary.status").value("CLOSED"));
        act(citizen, id, "feedback", Map.of("rating", 4, "comment", "Resolved well")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.feedback.rating").value(4));
        act(citizen, id, "feedback", Map.of("rating", 5)).andExpect(status().isConflict());
        act(citizen, id, "comments", Map.of("visibility", "PUBLIC", "body", "late")).andExpect(status().isConflict());
    }

    @Test
    void invalidTransitionsAndScopeAreEnforced() throws Exception {
        String id = create().get("summary").get("id").asText();
        act(officer, id, "resolve", Map.of("text", "skip ahead")).andExpect(status().isForbidden()); // not assignee
        act(officer, id, "start", null).andExpect(status().isForbidden());
        TestUser outsider = staff("OFFICER", createDepartment("OUT"));
        mvc.perform(get("/api/v1/grievances/" + id).header("Authorization", outsider.bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/grievances/" + id).header("Authorization", citizen().bearer())).andExpect(status().isNotFound());
        act(officer, id, "assign", Map.of("assigneeId", officer.id().toString())).andExpect(status().isForbidden());
        act(admin, id, "assign", Map.of("assigneeId", citizen.id().toString())).andExpect(status().isUnprocessableEntity());
        act(citizen, id, "close", null).andExpect(status().isConflict());
    }

    @Test
    void validatesInputAndListsByScope() throws Exception {
        mvc.perform(post("/api/v1/grievances").header("Authorization", citizen.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", department.toString(), "category", "MADE_UP",
                                "subject", "x", "description", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
        String id = create().get("summary").get("id").asText();
        mvc.perform(get("/api/v1/grievances").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
        mvc.perform(get("/api/v1/grievances").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
        mvc.perform(get("/api/v1/grievances/categories").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data", hasItem("SERVICE_DELAY")));
        mvc.perform(get("/api/v1/departments/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(department.toString())));
    }

    private JsonNode create() throws Exception {
        return body(mvc.perform(post("/api/v1/grievances").header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", department.toString(), "category", "SERVICE_DELAY",
                                "subject", "Certificate delayed", "description", "My certificate has been pending for weeks."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary.referenceNumber", startsWith("GRV-")))
                .andExpect(jsonPath("$.data.summary.status").value("SUBMITTED"))
                .andReturn()).get("data");
    }

    private ResultActions act(TestUser user, String id, String action, Map<String, Object> body) throws Exception {
        var request = post("/api/v1/grievances/" + id + "/" + action).header("Authorization", user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mvc.perform(request);
    }
}
