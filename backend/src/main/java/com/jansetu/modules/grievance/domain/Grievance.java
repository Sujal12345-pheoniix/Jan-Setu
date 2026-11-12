package com.jansetu.modules.grievance.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grievances")
public class Grievance extends AuditableEntity {

    public static final Duration REOPEN_WINDOW = Duration.ofDays(14);
    public static final int MAX_REOPENS = 1;

    @Column(name = "reference_number", nullable = false, length = 30, updatable = false)
    private String referenceNumber;

    @Column(name = "citizen_id", nullable = false, updatable = false)
    private UUID citizenId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Column(nullable = false, length = 50, updatable = false)
    private String category;

    @Column(nullable = false, length = 200, updatable = false)
    private String subject;

    @Column(nullable = false, length = 4000, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GrievanceStatus status;

    @Column(nullable = false, length = 10)
    private String priority;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_summary", length = 4000)
    private String resolutionSummary;

    @Column(name = "reopen_deadline")
    private Instant reopenDeadline;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Grievance() {
    }

    public Grievance(String referenceNumber, UUID citizenId, UUID departmentId, UUID applicationId, String category,
                     String subject, String description) {
        this.referenceNumber = referenceNumber;
        this.citizenId = citizenId;
        this.departmentId = departmentId;
        this.applicationId = applicationId;
        this.category = category;
        this.subject = subject;
        this.description = description;
        this.status = GrievanceStatus.SUBMITTED;
        this.priority = "NORMAL";
    }

    public GrievanceStatus moveTo(GrievanceStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw ApiException.invalidState("A grievance in status " + status + " cannot move to " + target + ".");
        }
        GrievanceStatus previous = status;
        status = target;
        switch (target) {
            case ACKNOWLEDGED -> acknowledgedAt = now;
            case RESOLVED -> {
                resolvedAt = now;
                reopenDeadline = now.plus(REOPEN_WINDOW);
            }
            case CLOSED -> closedAt = now;
            case REOPENED -> reopenCount++;
            default -> { }
        }
        if (acknowledgedAt == null && target != GrievanceStatus.SUBMITTED) {
            acknowledgedAt = now;
        }
        return previous;
    }

    public void assignTo(UUID assignee) {
        this.assigneeId = assignee;
    }

    public void resolveWith(String summary) {
        this.resolutionSummary = summary;
    }

    public boolean canReopen(Instant now) {
        return status == GrievanceStatus.RESOLVED && reopenCount < MAX_REOPENS
                && reopenDeadline != null && now.isBefore(reopenDeadline);
    }

    public boolean isOwnedBy(UUID userId) {
        return citizenId.equals(userId);
    }

    public boolean isAssignedTo(UUID userId) {
        return assigneeId != null && assigneeId.equals(userId);
    }

    public String getReferenceNumber() { return referenceNumber; }
    public UUID getCitizenId() { return citizenId; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getApplicationId() { return applicationId; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public GrievanceStatus getStatus() { return status; }
    public String getPriority() { return priority; }
    public UUID getAssigneeId() { return assigneeId; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionSummary() { return resolutionSummary; }
    public Instant getReopenDeadline() { return reopenDeadline; }
    public int getReopenCount() { return reopenCount; }
    public Instant getClosedAt() { return closedAt; }
}
