package com.jansetu.integration;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jansetu.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReportsAndNotificationsIT extends IntegrationTestBase {

    @Test
    void reportsAreScopedAndAggregateOnly() throws Exception {
        UUID dept = createDepartment("RPT");
        TestUser deptAdmin = staff("DEPARTMENT_ADMIN", dept);
        mvc.perform(get("/api/v1/reports/summary").header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").isNumber())
                .andExpect(jsonPath("$.data.range.from").isNotEmpty());
        mvc.perform(get("/api/v1/reports/application-volume").header("Authorization", deptAdmin.bearer()))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(30)));
        mvc.perform(get("/api/v1/reports/department-performance").header("Authorization", deptAdmin.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].departmentId").value(dept.toString()));
        mvc.perform(get("/api/v1/reports/workload").header("Authorization", deptAdmin.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/grievance-trends").header("Authorization", deptAdmin.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/summary").param("departmentId", createDepartment("NOPE").toString())
                        .header("Authorization", deptAdmin.bearer()))
                .andExpect(status().isNotFound());

        TestUser management = staff("MANAGEMENT_VIEWER");
        mvc.perform(get("/api/v1/reports/department-performance").header("Authorization", management.bearer()))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/v1/reports/summary").header("Authorization", citizen().bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/summary").param("from", "2026-01-01T00:00:00Z").param("to", "2025-01-01T00:00:00Z")
                        .header("Authorization", management.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notificationsCanBeReadAndPreferencesRespected() throws Exception {
        TestUser citizen = citizen();
        UUID dept = createDepartment("NTF");
        mvc.perform(put("/api/v1/notifications/preferences").header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("preferences", List.of(Map.of("channel", "IN_APP", "category", "GRIEVANCE", "enabled", false))))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/grievances").header("Authorization", citizen.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", dept.toString(), "category", "OTHER", "subject", "Muted test",
                                "description", "Grievance notifications are muted for this user."))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.data.unread").value(0));

        TestUser other = citizen();
        mvc.perform(post("/api/v1/grievances").header("Authorization", other.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", dept.toString(), "category", "OTHER", "subject", "Loud test",
                                "description", "Grievance notifications are enabled for this user."))))
                .andExpect(status().isCreated());
        String notificationId = body(mvc.perform(get("/api/v1/notifications").param("unreadOnly", "true")
                        .header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andReturn()).at("/data/content/0/id").asText();
        mvc.perform(post("/api/v1/notifications/" + notificationId + "/read").header("Authorization", citizen.bearer()))
                .andExpect(status().isNotFound()); // not the owner
        mvc.perform(post("/api/v1/notifications/" + notificationId + "/read").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.unread").value(0));
    }

    @Test
    void auditorCanExportCsvWithFormulaProtection() throws Exception {
        TestUser auditor = staff("AUDITOR");
        mvc.perform(get("/api/v1/audit-logs/export").header("Authorization", auditor.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("occurredAt,actorId")));
        mvc.perform(get("/api/v1/audit-logs/export").header("Authorization", staff("PLATFORM_ADMIN").bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/activity").header("Authorization", auditor.bearer()))
                .andExpect(jsonPath("$.data[*].action", org.hamcrest.Matchers.hasItem("LOGIN_SUCCEEDED")));
    }
}
