package com.jansetu.modules.catalogue.domain;

import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;

/** A requirement (document, declaration or information) of a service's working copy. */
@Entity
@Table(name = "service_requirements")
public class ServiceRequirement extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ServiceDefinition service;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 20)
    private RequirementType requirementType;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(name = "allowed_media_types", length = 500)
    private String allowedMediaTypes;

    @Column(name = "max_size_bytes")
    private Long maxSizeBytes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ServiceRequirement() {
    }

    public ServiceRequirement(String code, String name, String description, RequirementType requirementType,
                              boolean mandatory, String allowedMediaTypes, Long maxSizeBytes, int displayOrder) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.requirementType = requirementType;
        this.mandatory = mandatory;
        this.allowedMediaTypes = allowedMediaTypes;
        this.maxSizeBytes = maxSizeBytes;
        this.displayOrder = displayOrder;
    }

    void attachTo(ServiceDefinition owner) {
        this.service = owner;
    }

    public void update(String newName, String newDescription, Boolean newMandatory, String newMediaTypes,
                       Long newMaxSize, Integer newDisplayOrder) {
        if (newName != null) {
            name = newName;
        }
        if (newDescription != null) {
            description = newDescription.isBlank() ? null : newDescription;
        }
        if (newMandatory != null) {
            mandatory = newMandatory;
        }
        if (newMediaTypes != null) {
            allowedMediaTypes = newMediaTypes.isBlank() ? null : newMediaTypes;
        }
        if (newMaxSize != null) {
            maxSizeBytes = newMaxSize;
        }
        if (newDisplayOrder != null) {
            displayOrder = newDisplayOrder;
        }
    }

    /** Snapshot representation stored in the immutable service version. */
    public Map<String, Object> toSnapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("description", description);
        map.put("requirementType", requirementType.name());
        map.put("mandatory", mandatory);
        map.put("allowedMediaTypes", allowedMediaTypes);
        map.put("maxSizeBytes", maxSizeBytes);
        map.put("displayOrder", displayOrder);
        return map;
    }

    public ServiceDefinition getService() {
        return service;
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

    public RequirementType getRequirementType() {
        return requirementType;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getAllowedMediaTypes() {
        return allowedMediaTypes;
    }

    public Long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
