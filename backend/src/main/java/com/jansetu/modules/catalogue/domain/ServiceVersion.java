package com.jansetu.modules.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable published snapshot of a service (also protected by a database trigger). */
@Entity
@Immutable
@Table(name = "service_versions")
public class ServiceVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> requirements;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    protected ServiceVersion() {
    }

    public ServiceVersion(ServiceDefinition service, int versionNumber, Instant publishedAt, UUID publishedBy) {
        this.serviceId = service.getId();
        this.versionNumber = versionNumber;
        this.name = service.getName();
        this.description = service.getDescription();
        this.category = service.getCategory();
        this.eligibilityText = service.getEligibilityText();
        this.processingDays = service.getProcessingDays();
        this.feeAmount = service.getFeeAmount();
        this.formSchema = new java.util.LinkedHashMap<>(service.getFormSchema());
        this.requirements = service.getRequirements().stream().map(ServiceRequirement::toSnapshot).toList();
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public int getVersionNumber() {
        return versionNumber;
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

    public List<Map<String, Object>> getRequirements() {
        return requirements;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
