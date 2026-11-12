package com.jansetu.modules.catalogue.api;

import com.jansetu.modules.catalogue.domain.RequirementType;
import com.jansetu.modules.catalogue.domain.ServiceDefinition;
import com.jansetu.modules.catalogue.domain.ServiceRequirement;
import com.jansetu.modules.catalogue.domain.ServiceVersion;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService.PublishedService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogueDtos {

    private CatalogueDtos() {
    }

    static final String CATEGORY_REGEX = "^[A-Za-z][A-Za-z_]{1,49}$";

    // ---- Public catalogue -------------------------------------------------------------------------------------

    public record PublicServiceSummary(UUID id, String code, UUID departmentId, String name, String category,
                                       String description, int processingDays, BigDecimal feeAmount, int version,
                                       Instant publishedAt) {
        static PublicServiceSummary from(PublishedService p) {
            ServiceVersion v = p.version();
            return new PublicServiceSummary(p.serviceId(), p.code(), p.departmentId(), v.getName(), v.getCategory(),
                    v.getDescription(), v.getProcessingDays(), v.getFeeAmount(), v.getVersionNumber(), v.getPublishedAt());
        }
    }

    public record PublicServiceDetail(UUID id, String code, UUID departmentId, int version, String name,
                                      String category, String description, String eligibilityText,
                                      int processingDays, BigDecimal feeAmount, Map<String, Object> formSchema,
                                      List<Map<String, Object>> requirements, Instant publishedAt) {
        static PublicServiceDetail from(PublishedService p) {
            ServiceVersion v = p.version();
            return new PublicServiceDetail(p.serviceId(), p.code(), p.departmentId(), v.getVersionNumber(), v.getName(),
                    v.getCategory(), v.getDescription(), v.getEligibilityText(), v.getProcessingDays(),
                    v.getFeeAmount(), v.getFormSchema(), v.getRequirements(), v.getPublishedAt());
        }
    }

    // ---- Management -------------------------------------------------------------------------------------------

    public record RequirementResponse(UUID id, String code, String name, String description, String requirementType,
                                      boolean mandatory, String allowedMediaTypes, Long maxSizeBytes, int displayOrder,
                                      long version) {
        static RequirementResponse from(ServiceRequirement r) {
            return new RequirementResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                    r.getRequirementType().name(), r.isMandatory(), r.getAllowedMediaTypes(), r.getMaxSizeBytes(),
                    r.getDisplayOrder(), r.getVersion());
        }
    }

    public record ServiceSummaryResponse(UUID id, UUID departmentId, String code, String name, String category,
                                         String status, int currentVersion, boolean hasUnpublishedChanges,
                                         Instant updatedAt, long version) {
        static ServiceSummaryResponse from(ServiceDefinition s) {
            return new ServiceSummaryResponse(s.getId(), s.getDepartmentId(), s.getCode(), s.getName(), s.getCategory(),
                    s.getStatus().name(), s.getCurrentVersion(), s.isHasUnpublishedChanges(), s.getUpdatedAt(),
                    s.getVersion());
        }
    }

    public record ServiceResponse(UUID id, UUID departmentId, String code, String name, String description,
                                  String category, String eligibilityText, int processingDays, BigDecimal feeAmount,
                                  Map<String, Object> formSchema, String status, int currentVersion,
                                  boolean hasUnpublishedChanges, List<RequirementResponse> requirements,
                                  Instant createdAt, Instant updatedAt, long version) {
        public static ServiceResponse from(ServiceDefinition s) {
            return new ServiceResponse(s.getId(), s.getDepartmentId(), s.getCode(), s.getName(), s.getDescription(),
                    s.getCategory(), s.getEligibilityText(), s.getProcessingDays(), s.getFeeAmount(), s.getFormSchema(),
                    s.getStatus().name(), s.getCurrentVersion(), s.isHasUnpublishedChanges(),
                    s.getRequirements().stream().map(RequirementResponse::from).toList(),
                    s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
        }
    }

    public record CreateServiceRequest(
            @NotNull UUID departmentId,
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,49}$",
                    message = "must be 2-50 letters, digits, '-' or '_', starting with a letter") String code,
            @NotBlank @Size(min = 3, max = 200) String name,
            @NotBlank @Size(max = 4000) String description,
            @NotBlank @Pattern(regexp = CATEGORY_REGEX, message = "must be letters or underscores") String category,
            @Size(max = 4000) String eligibilityText,
            @NotNull @Min(1) @Max(365) Integer processingDays,
            @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal feeAmount,
            Map<String, Object> formSchema) {
    }

    public record UpdateServiceRequest(
            @Size(min = 3, max = 200) String name,
            @Size(max = 4000) String description,
            @Pattern(regexp = CATEGORY_REGEX, message = "must be letters or underscores") String category,
            @Size(max = 4000) String eligibilityText,
            @Min(1) @Max(365) Integer processingDays,
            @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal feeAmount,
            Map<String, Object> formSchema,
            @NotNull Long version) {
    }

    public record PublishRequest(Long version) {
    }

    public record CreateRequirementRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$",
                    message = "must be 2-50 letters, digits or underscores, starting with a letter") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull RequirementType requirementType,
            Boolean mandatory,
            @Size(max = 500) String allowedMediaTypes,
            @Positive @Max(52_428_800) Long maxSizeBytes,
            @Min(0) @Max(1000) Integer displayOrder) {
    }

    public record UpdateRequirementRequest(
            @Size(min = 1, max = 200) String name,
            @Size(max = 1000) String description,
            Boolean mandatory,
            @Size(max = 500) String allowedMediaTypes,
            @Positive @Max(52_428_800) Long maxSizeBytes,
            @Min(0) @Max(1000) Integer displayOrder) {
    }

    public record ServiceVersionResponse(UUID serviceId, int version, Instant publishedAt) {
        static ServiceVersionResponse from(ServiceVersion v) {
            return new ServiceVersionResponse(v.getServiceId(), v.getVersionNumber(), v.getPublishedAt());
        }
    }
}
