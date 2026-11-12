package com.jansetu.modules.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One running workflow per submitted application (partial unique index enforces a single ACTIVE instance). */
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    public static final String STANDARD = "STANDARD_REVIEW_APPROVAL_V1";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "definition_version_id", updatable = false)
    private UUID definitionVersionId;

    @Column(name = "workflow_code", nullable = false, length = 50, updatable = false)
    private String workflowCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "current_step_code", length = 50)
    private String currentStepCode;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected WorkflowInstance() {
    }

    public WorkflowInstance(UUID applicationId, Instant now) {
        this.applicationId = applicationId;
        this.workflowCode = STANDARD;
        this.status = "ACTIVE";
        this.currentStepCode = WorkflowTask.REVIEW;
        this.startedAt = now;
    }

    public void moveTo(String step) {
        this.currentStepCode = step;
    }

    public void finish(String finalStatus, Instant now) {
        this.status = finalStatus;
        this.currentStepCode = null;
        this.completedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getWorkflowCode() { return workflowCode; }
    public String getStatus() { return status; }
    public String getCurrentStepCode() { return currentStepCode; }
}
