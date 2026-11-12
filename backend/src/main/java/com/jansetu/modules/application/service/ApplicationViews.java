package com.jansetu.modules.application.service;

import com.jansetu.modules.document.service.DocumentService.DocumentView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read models returned by the application module. Staff-only parts are null for citizens. */
public final class ApplicationViews {

    private ApplicationViews() {
    }

    public record Summary(UUID id, String referenceNumber, String status, UUID serviceId, String serviceName,
                          UUID departmentId, UUID citizenId, String applicantName, Instant createdAt,
                          Instant submittedAt, Instant decidedAt, Instant updatedAt, String currentStep,
                          String taskStatus, UUID assigneeId, String assigneeName, Instant dueAt, boolean overdue,
                          long version) {
    }

    public record HistoryItem(String fromStatus, String toStatus, String command, String actorName, String reason,
                              Instant occurredAt) {
    }

    public record DecisionItem(String decision, String reason, String decidedByName, Instant decidedAt) {
    }

    public record ClarificationItem(UUID id, String question, Instant dueAt, String response, Instant respondedAt,
                                    String status, Instant createdAt) {
    }

    public record TaskItem(UUID id, String step, String status, UUID assigneeId, String assigneeName, Instant dueAt,
                           String outcome, Instant createdAt, Instant completedAt, long version) {
    }

    public record CommentItem(UUID id, String authorName, String body, Instant createdAt) {
    }

    public record ServiceInfo(UUID serviceVersionId, int versionNumber, String code, String name, int processingDays,
                              Map<String, Object> formSchema, List<Map<String, Object>> requirements) {
    }

    public record Detail(Summary summary, ServiceInfo service, Map<String, Object> formData,
                         List<DocumentView> documents, List<ClarificationItem> clarifications,
                         List<DecisionItem> decisions, List<HistoryItem> history, List<TaskItem> tasks,
                         List<CommentItem> comments, List<String> availableActions) {
    }
}
