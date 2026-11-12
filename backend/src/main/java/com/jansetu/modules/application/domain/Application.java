package com.jansetu.modules.application.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "applications")
public class Application extends AuditableEntity {

    @Column(name = "reference_number", length = 30)
    private String referenceNumber;

    @Column(name = "citizen_id", nullable = false, updatable = false)
    private UUID citizenId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "service_version_id", nullable = false)
    private UUID serviceVersionId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> formData;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Application() {
    }

    public Application(UUID citizenId, UUID serviceId, UUID serviceVersionId, UUID departmentId) {
        this.citizenId = citizenId;
        this.serviceId = serviceId;
        this.serviceVersionId = serviceVersionId;
        this.departmentId = departmentId;
        this.status = ApplicationStatus.DRAFT;
        this.formData = new LinkedHashMap<>();
    }

    /** Moves to {@code target}, rejecting any transition the state machine does not allow. */
    public ApplicationStatus transitionTo(ApplicationStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw ApiException.invalidState("An application in status " + status + " cannot move to " + target + ".");
        }
        ApplicationStatus previous = status;
        status = target;
        switch (target) {
            case SUBMITTED -> submittedAt = now;
            case APPROVED, REJECTED -> decidedAt = now;
            case COMPLETED -> completedAt = now;
            case WITHDRAWN -> withdrawnAt = now;
            default -> { }
        }
        return previous;
    }

    public void replaceFormData(Map<String, Object> data) {
        if (status != ApplicationStatus.DRAFT) {
            throw ApiException.invalidState("Only a draft application can be edited.");
        }
        this.formData = new LinkedHashMap<>(data);
    }

    public void assignReference(String reference) {
        if (referenceNumber == null) {
            referenceNumber = reference;
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return citizenId.equals(userId);
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public UUID getCitizenId() {
        return citizenId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getServiceVersionId() {
        return serviceVersionId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Map<String, Object> getFormData() {
        return formData;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
