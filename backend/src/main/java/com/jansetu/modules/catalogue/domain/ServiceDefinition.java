package com.jansetu.modules.catalogue.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Editable working copy of a service ("services" table). Citizens never see this directly: publishing writes an
 * immutable {@link ServiceVersion} snapshot, and the public catalogue serves the latest snapshot.
 */
@Entity
@Table(name = "services")
public class ServiceDefinition extends AuditableEntity {

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "eligibility_text", length = 4000)
    private String eligibilityText;

    @Column(name = "processing_days", nullable = false)
    private int processingDays;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> formSchema;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "has_unpublished_changes", nullable = false)
    private boolean hasUnpublishedChanges;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, code ASC")
    private List<ServiceRequirement> requirements = new ArrayList<>();

    protected ServiceDefinition() {
    }

    public ServiceDefinition(UUID departmentId, String code, String name, String description, String category,
                             String eligibilityText, int processingDays, BigDecimal feeAmount,
                             Map<String, Object> formSchema) {
        this.departmentId = departmentId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.category = category;
        this.eligibilityText = eligibilityText;
        this.processingDays = processingDays;
        this.feeAmount = feeAmount;
        this.formSchema = formSchema;
        this.status = ServiceStatus.DRAFT;
        this.currentVersion = 0;
        this.hasUnpublishedChanges = true;
    }

    public void ensureEditable() {
        if (status == ServiceStatus.DEPRECATED) {
            throw ApiException.invalidState("A deprecated service cannot be edited.");
        }
    }

    public void updateDetails(String newName, String newDescription, String newCategory, String newEligibility,
                              Integer newProcessingDays, BigDecimal newFee, Map<String, Object> newFormSchema) {
        ensureEditable();
        if (newName != null) {
            name = newName;
        }
        if (newDescription != null) {
            description = newDescription;
        }
        if (newCategory != null) {
            category = newCategory;
        }
        if (newEligibility != null) {
            eligibilityText = newEligibility.isBlank() ? null : newEligibility;
        }
        if (newProcessingDays != null) {
            processingDays = newProcessingDays;
        }
        if (newFee != null) {
            feeAmount = newFee;
        }
        if (newFormSchema != null) {
            formSchema = newFormSchema;
        }
        markChanged();
    }

    public ServiceRequirement addRequirement(ServiceRequirement requirement) {
        ensureEditable();
        if (findRequirementByCode(requirement.getCode()).isPresent()) {
            throw ApiException.duplicate("A requirement with code " + requirement.getCode() + " already exists.");
        }
        requirement.attachTo(this);
        requirements.add(requirement);
        markChanged();
        return requirement;
    }

    public void removeRequirement(ServiceRequirement requirement) {
        ensureEditable();
        requirements.remove(requirement);
        markChanged();
    }

    public Optional<ServiceRequirement> findRequirement(UUID requirementId) {
        return requirements.stream().filter(r -> r.getId() != null && r.getId().equals(requirementId)).findFirst();
    }

    private Optional<ServiceRequirement> findRequirementByCode(String requirementCode) {
        return requirements.stream().filter(r -> r.getCode().equals(requirementCode)).findFirst();
    }

    public void markChanged() {
        hasUnpublishedChanges = true;
    }

    /** Transition to PUBLISHED with the next version number. The caller persists the snapshot. */
    public int publish() {
        ensureEditable();
        if (status == ServiceStatus.PUBLISHED && !hasUnpublishedChanges) {
            throw ApiException.invalidState("There are no unpublished changes.");
        }
        currentVersion++;
        status = ServiceStatus.PUBLISHED;
        hasUnpublishedChanges = false;
        return currentVersion;
    }

    public void deprecate() {
        if (status != ServiceStatus.PUBLISHED) {
            throw ApiException.invalidState("Only a published service can be deprecated.");
        }
        status = ServiceStatus.DEPRECATED;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getEligibilityText() {
        return eligibilityText;
    }

    public int getProcessingDays() {
        return processingDays;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public Map<String, Object> getFormSchema() {
        return formSchema;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public boolean isHasUnpublishedChanges() {
        return hasUnpublishedChanges;
    }

    public List<ServiceRequirement> getRequirements() {
        return requirements;
    }
}
