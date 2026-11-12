package com.jansetu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jansetu.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end application workflow through the real API, database and authorization rules. */
class ApplicationLifecycleIT extends IntegrationTestBase {

    private UUID department;
    private UUID serviceId;
    private TestUser citizen;
    private TestUser officer;
    private TestUser reviewer;

    @BeforeEach
    void setUp() {
        department = createDepartment("CASE");
        serviceId = publishedService(department);
        citizen = citizen();
        officer = staff("OFFICER", department);
        reviewer = staff("REVIEWER", department);
    }

    @Test
    void fullLifecycleWithClarificationApprovalAndCompletion() throws Exception {
        String id = submitValidApplication();

        // the citizen's application is private to them and the department
        TestUser stranger = citizen();
        mvc.perform(get("/api/v1/applications/" + id).header("Authorization", stranger.bearer())).andExpect(status().isNotFound());
        TestUser otherOfficer = staff("OFFICER", createDepartment("OTHER"));
        mvc.perform(get("/api/v1/applications/" + id).header("Authorization", otherOfficer.bearer())).andExpect(status().isNotFound());

        // officer queue shows the new review task
        mvc.perform(get("/api/v1/tasks").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.content[*].applicationId", hasItem(id)));
        staffAction(officer, id, "start-review", null).andExpect(jsonPath("$.data.summary.status").value("UNDER_REVIEW"));

        // clarification round-trip
        staffAction(officer, id, "request-clarification", Map.of("question", "Please upload a clearer ID proof.", "dueInDays", 5))
                .andExpect(jsonPath("$.data.summary.status").value("CLARIFICATION_REQUIRED"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.content[*].type", hasItem("CLARIFICATION_REQUESTED")));
        upload(citizen, id, "ID_PROOF", "id-v2.pdf", "application/pdf", PDF).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentVersion").value(2));
        mvc.perform(post("/api/v1/applications/" + id + "/clarification-response").header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("response", "Uploaded a clearer copy."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("RESUBMITTED"));
        staffAction(officer, id, "start-review", null).andExpect(jsonPath("$.data.summary.status").value("UNDER_REVIEW"));

        // documents must be verified before recommending
        staffAction(officer, id, "recommend", Map.of("reason", "Looks fine")).andExpect(status().isUnprocessableEntity());
        String docId = body(mvc.perform(get("/api/v1/applications/" + id).header("Authorization", officer.bearer())).andReturn())
                .at("/data/documents/0/id").asText();
        mvc.perform(post("/api/v1/applications/" + id + "/documents/" + docId + "/verification")
                        .header("Authorization", officer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accept", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("VERIFIED"));
        staffAction(officer, id, "recommend", Map.of("reason", "All checks passed"))
                .andExpect(jsonPath("$.data.summary.status").value("PENDING_APPROVAL"));

        // reviewer approves, then completes
        staffAction(reviewer, id, "approve", Map.of("reason", "Approved")).andExpect(jsonPath("$.data.summary.status").value("APPROVED"));
        staffAction(reviewer, id, "complete", null).andExpect(jsonPath("$.data.summary.status").value("COMPLETED"));

        // citizen view: timeline and final decision, but no internal tasks/comments/recommendations
        JsonNode detail = body(mvc.perform(get("/api/v1/applications/" + id).header("Authorization", citizen.bearer()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(detail.has("tasks")).isFalse();     // staff-only data is not serialised for citizens
        assertThat(detail.has("comments")).isFalse();
        assertThat(detail.get("decisions").findValuesAsText("decision")).containsExactly("APPROVED");
        assertThat(detail.get("history").findValuesAsText("toStatus"))
                .containsSubsequence("DRAFT", "SUBMITTED", "UNDER_REVIEW", "CLARIFICATION_REQUIRED", "RESUBMITTED",
                        "APPROVED", "COMPLETED");
        assertThat(detail.get("history").findValuesAsText("toStatus")).doesNotContain("PENDING_APPROVAL");
    }

    @Test
    void submissionValidatesFormAndDocumentsAndIsSingleShot() throws Exception {
        JsonNode draft = createDraft();
        String id = draft.at("/summary/id").asText();
        mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", citizen.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("formData.applicantName")));

        saveDraft(id, Map.of("applicantName", "Asha Demo", "annualIncome", 120000, "_declarations", List.of("TRUTH")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", citizen.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Identity proof")));

        // file validation happens on the server
        upload(citizen, id, "ID_PROOF", "fake.pdf", "application/pdf", "not really a pdf".getBytes())
                .andExpect(status().isBadRequest());
        upload(citizen, id, "ID_PROOF", "id.png", "image/png", PDF).andExpect(status().isBadRequest());
        upload(citizen, id, "UNKNOWN", "id.pdf", "application/pdf", PDF).andExpect(status().isUnprocessableEntity());
        upload(citizen, id, "ID_PROOF", "id.pdf", "application/pdf", PDF).andExpect(status().isCreated());

        String key = "submit-" + uniq();
        String ref = body(mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", citizen.bearer())
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceNumber", startsWith("JAN-")))
                .andReturn()).at("/data/referenceNumber").asText();
        // retry with the same key returns the same result; a fresh submit is rejected by the state machine
        mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", citizen.bearer())
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.referenceNumber").value(ref));
        mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", citizen.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INVALID_STATE"));
        // drafts cannot be edited after submission
        saveDraft(id, Map.of("applicantName", "Changed")).andExpect(status().isConflict());
    }

    @Test
    void draftRejectsUnknownFieldsAndWrongTypes() throws Exception {
        String id = createDraft().at("/summary/id").asText();
        saveDraft(id, Map.of("annualIncome", "lots")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("formData.annualIncome"));
        saveDraft(id, Map.of("injected", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAssignedOfficerActsAndRejectionNeedsReason() throws Exception {
        String id = submitValidApplication();
        TestUser colleague = staff("OFFICER", department);
        staffAction(officer, id, "start-review", null).andExpect(status().isOk());
        staffAction(colleague, id, "request-clarification", Map.of("question", "Hijack?")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/applications/" + id + "/reject").header("Authorization", officer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of())))
                .andExpect(status().isBadRequest());
        staffAction(officer, id, "reject", Map.of("reason", "Income outside scheme limits"))
                .andExpect(jsonPath("$.data.summary.status").value("REJECTED"));
        // terminal state
        staffAction(officer, id, "start-review", null).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/applications/" + id).header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.decisions[0].decision").value("REJECTED"))
                .andExpect(jsonPath("$.data.decisions[0].reason").value("Income outside scheme limits"));
    }

    @Test
    void reviewerCanReturnForCorrectionAndRecommenderCannotApprove() throws Exception {
        String id = submitValidApplication();
        TestUser dualRole = staff("OFFICER", department);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/" + dualRole.id() + "/roles")
                        .header("Authorization", staff("PLATFORM_ADMIN").bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("OFFICER", "REVIEWER")))))
                .andExpect(status().isOk());
        reviewToRecommendation(dualRole, id);
        // separation of duties: the recommending officer cannot approve
        staffAction(dualRole, id, "approve", null).andExpect(status().isUnprocessableEntity());
        staffAction(reviewer, id, "return", Map.of("reason", "Re-check income evidence"))
                .andExpect(jsonPath("$.data.summary.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.summary.assigneeId").value(dualRole.id().toString()));
        staffAction(dualRole, id, "recommend", Map.of("reason", "Re-checked")).andExpect(status().isOk());
        staffAction(reviewer, id, "approve", null).andExpect(jsonPath("$.data.summary.status").value("APPROVED"));
    }

    @Test
    void departmentAdminAssignsTasksOnlyToEligibleMembers() throws Exception {
        String id = submitValidApplication();
        TestUser admin = staff("DEPARTMENT_ADMIN", department);
        String taskId = body(mvc.perform(get("/api/v1/tasks").param("unassigned", "true").header("Authorization", admin.bearer()))
                .andReturn()).at("/data/content").findValuesAsText("taskId").stream()
                .filter(t -> true).findFirst().orElseThrow();
        // a reviewer cannot take a REVIEW task; an officer from another department cannot either
        assign(admin, taskId, reviewer.id()).andExpect(status().isUnprocessableEntity());
        assign(admin, taskId, staff("OFFICER", createDepartment("ELSE")).id()).andExpect(status().isUnprocessableEntity());
        assign(admin, taskId, officer.id()).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks").param("mine", "true").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.content[*].applicationId", hasItem(id)));
        mvc.perform(get("/api/v1/notifications").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.content[*].type", hasItem("TASK_ASSIGNED")));
        // officers cannot assign
        assign(officer, taskId, officer.id()).andExpect(status().isForbidden());
    }

    @Test
    void citizenCanWithdrawBeforeReviewAndDownloadOwnDocumentOnly() throws Exception {
        String id = submitValidApplication();
        String docId = body(mvc.perform(get("/api/v1/applications/" + id).header("Authorization", citizen.bearer())).andReturn())
                .at("/data/documents/0/id").asText();
        mvc.perform(get("/api/v1/applications/" + id + "/documents/" + docId + "/download").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get("/api/v1/applications/" + id + "/documents/" + docId + "/download").header("Authorization", citizen().bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/applications/" + id + "/withdraw").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.summary.status").value("WITHDRAWN"));
        mvc.perform(get("/api/v1/tasks").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.content[*].applicationId", not(hasItem(id))));
    }

    @Test
    void staffListsExcludeDraftsAndSupportFilters() throws Exception {
        String draftId = createDraft().at("/summary/id").asText();
        String submitted = submitValidApplication();
        mvc.perform(get("/api/v1/applications").param("size", "100").header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(submitted)))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(draftId))));
        mvc.perform(get("/api/v1/applications").param("status", "SUBMITTED").param("serviceId", serviceId.toString())
                        .header("Authorization", officer.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/applications").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private JsonNode createDraft() throws Exception {
        return body(mvc.perform(post("/api/v1/applications").header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("serviceId", serviceId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.availableActions", hasItem("SUBMIT")))
                .andReturn()).get("data");
    }

    private ResultActions saveDraft(String id, Map<String, Object> formData) throws Exception {
        long version = body(mvc.perform(get("/api/v1/applications/" + id).header("Authorization", citizen.bearer()))
                .andReturn()).at("/data/summary/version").asLong();
        return mvc.perform(patch("/api/v1/applications/" + id).header("Authorization", citizen.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("formData", formData, "version", version))));
    }

    private ResultActions upload(TestUser user, String id, String requirement, String name, String type, byte[] bytes)
            throws Exception {
        return mvc.perform(multipart("/api/v1/applications/" + id + "/documents")
                .file(new MockMultipartFile("file", name, type, bytes))
                .param("requirementCode", requirement)
                .header("Authorization", user.bearer()));
    }

    private String submitValidApplication() throws Exception {
        String id = createDraft().at("/summary/id").asText();
        saveDraft(id, Map.of("applicantName", "Asha Demo", "annualIncome", 120000, "_declarations", List.of("TRUTH")))
                .andExpect(status().isOk());
        upload(citizen, id, "ID_PROOF", "id.pdf", "application/pdf", PDF).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        return id;
    }

    private void reviewToRecommendation(TestUser reviewingOfficer, String id) throws Exception {
        staffAction(reviewingOfficer, id, "start-review", null).andExpect(status().isOk());
        String docId = body(mvc.perform(get("/api/v1/applications/" + id).header("Authorization", reviewingOfficer.bearer()))
                .andReturn()).at("/data/documents/0/id").asText();
        mvc.perform(post("/api/v1/applications/" + id + "/documents/" + docId + "/verification")
                        .header("Authorization", reviewingOfficer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accept", true))))
                .andExpect(status().isOk());
        staffAction(reviewingOfficer, id, "recommend", null).andExpect(jsonPath("$.data.summary.status").value("PENDING_APPROVAL"));
    }

    private ResultActions staffAction(TestUser user, String id, String action, Map<String, Object> body) throws Exception {
        var request = post("/api/v1/applications/" + id + "/" + action).header("Authorization", user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mvc.perform(request);
    }

    private ResultActions assign(TestUser actor, String taskId, UUID assignee) throws Exception {
        return mvc.perform(post("/api/v1/tasks/" + taskId + "/assign").header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("assigneeId", assignee.toString()))));
    }
}
