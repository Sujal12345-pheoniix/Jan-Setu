package com.jansetu.modules.application.domain;

import com.jansetu.common.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A unit of work in the built-in workflow: a REVIEW task (officer) followed by an APPROVAL task (reviewer).
 * Task status is separate from application status (docs/WORKFLOW.md §1).
 */
@Entity
@Table(name = "workflow_tasks")
public class WorkflowTask {

    public static final String REVIEW = "REVIEW";
    public static final String APPROVAL = "APPROVAL";
    private static final Set<String> ACTIVE = Set.of("OPEN", "ASSIGNED", "IN_PROGRESS", "BLOCKED");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_instance_id", nullable = false, updatable = false)
    private UUID workflowInstanceId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "step_code", nullable = false, length = 50, updatable = false)
    private String stepCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(length = 30)
    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected WorkflowTask() {
    }

    public WorkflowTask(UUID workflowInstanceId, UUID applicationId, UUID departmentId, String stepCode, Instant dueAt,
                        Instant now) {
        this.workflowInstanceId = workflowInstanceId;
        this.applicationId = applicationId;
        this.departmentId = departmentId;
        this.stepCode = stepCode;
        this.status = "OPEN";
        this.dueAt = dueAt;
        this.createdAt = now;
    }

    public boolean isActive() {
        return ACTIVE.contains(status);
    }

    public void assign(UUID assignee, Instant now) {
        requireActive();
        this.assigneeId = assignee;
        if ("OPEN".equals(status)) {
            status = "ASSIGNED";
        }
        this.claimedAt = now;
    }

    /** The assignee (or a claimant of an unassigned task) starts working on it. */
    public void start(UUID actor, Instant now) {
        requireActive();
        if (assigneeId != null && !assigneeId.equals(actor)) {
            throw ApiException.accessDenied("This task is assigned to another user.");
        }
        if (assigneeId == null) {
            assigneeId = actor;
            claimedAt = now;
        }
        status = "IN_PROGRESS";
    }

    public void block() {
        requireActive();
        status = "BLOCKED";
    }

    /** Clarification answered: back to the assignee, with the SLA clock extended by the time spent waiting. */
    public void resume(Duration paused) {
        if (!"BLOCKED".equals(status)) {
            throw ApiException.invalidState("The task is not waiting for clarification.");
        }
        status = "ASSIGNED";
        if (dueAt != null && paused != null && !paused.isNegative()) {
            dueAt = dueAt.plus(paused);
        }
    }

    public void complete(String result, Instant now) {
        requireActive();
        status = "COMPLETED";
        outcome = result;
        completedAt = now;
    }

    public void cancel(Instant now) {
        if (isActive()) {
            status = "CANCELLED";
            completedAt = now;
        }
    }

    public boolean isAssignedTo(UUID userId) {
        return assigneeId != null && assigneeId.equals(userId);
    }

    private void requireActive() {
        if (!isActive()) {
            throw ApiException.invalidState("This task is no longer active.");
        }
    }

    public UUID getId() { return id; }
    public UUID getWorkflowInstanceId() { return workflowInstanceId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getStepCode() { return stepCode; }
    public String getStatus() { return status; }
    public UUID getAssigneeId() { return assigneeId; }
    public Instant getDueAt() { return dueAt; }
    public String getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
}
