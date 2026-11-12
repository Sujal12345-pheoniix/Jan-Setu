package com.jansetu.modules.catalogue.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.catalogue.api.CatalogueDtos.CreateRequirementRequest;
import com.jansetu.modules.catalogue.api.CatalogueDtos.CreateServiceRequest;
import com.jansetu.modules.catalogue.api.CatalogueDtos.PublicServiceDetail;
import com.jansetu.modules.catalogue.api.CatalogueDtos.PublicServiceSummary;
import com.jansetu.modules.catalogue.api.CatalogueDtos.PublishRequest;
import com.jansetu.modules.catalogue.api.CatalogueDtos.RequirementResponse;
import com.jansetu.modules.catalogue.api.CatalogueDtos.ServiceResponse;
import com.jansetu.modules.catalogue.api.CatalogueDtos.ServiceSummaryResponse;
import com.jansetu.modules.catalogue.api.CatalogueDtos.ServiceVersionResponse;
import com.jansetu.modules.catalogue.api.CatalogueDtos.UpdateRequirementRequest;
import com.jansetu.modules.catalogue.api.CatalogueDtos.UpdateServiceRequest;
import com.jansetu.modules.catalogue.domain.ServiceStatus;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService.PublicFilter;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService.CreateServiceCommand;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService.ManageFilter;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService.RequirementCommand;
import com.jansetu.modules.catalogue.service.ServiceCatalogueService.UpdateServiceCommand;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Service catalogue", description = "Public catalogue and department-scoped service management")
public class ServiceCatalogueController {

    private final ServiceCatalogueService catalogueService;
    private final ServiceCatalogueQueryService queryService;
    private final CurrentUser currentUser;

    public ServiceCatalogueController(ServiceCatalogueService catalogueService, ServiceCatalogueQueryService queryService,
                                      CurrentUser currentUser) {
        this.catalogueService = catalogueService;
        this.queryService = queryService;
        this.currentUser = currentUser;
    }

    // ---- Public ---------------------------------------------------------------------------------------------

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Browse published services (public)", description = "Sortable by name, category, processingDays, publishedAt.")
    public ApiResponse<PageResponse<PublicServiceSummary>> browse(@RequestParam(required = false) UUID departmentId,
                                                                  @RequestParam(required = false) String category,
                                                                  @RequestParam(required = false) String q,
                                                                  @ParameterObject PageQuery page) {
        return ApiResponse.of(PageResponse.from(queryService.listPublished(new PublicFilter(departmentId, category, q), page),
                PublicServiceSummary::from));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Get the latest published version of a service (public)")
    public ApiResponse<PublicServiceDetail> published(@PathVariable UUID id) {
        return ApiResponse.of(PublicServiceDetail.from(queryService.getPublished(id)));
    }

    @GetMapping("/{id}/versions/{version}")
    @SecurityRequirements
    @Operation(summary = "Get an immutable published version (public)")
    public ApiResponse<PublicServiceDetail> version(@PathVariable UUID id, @PathVariable int version) {
        return ApiResponse.of(PublicServiceDetail.from(queryService.getVersion(id, version)));
    }

    // ---- Management -----------------------------------------------------------------------------------------

    @GetMapping("/manage")
    @PreAuthorize("hasAuthority('SERVICE:READ_ALL')")
    @Operation(summary = "List service working copies in your scope (including drafts)",
            description = "Sortable by code, name, status, createdAt, updatedAt.")
    public ApiResponse<PageResponse<ServiceSummaryResponse>> manageList(@RequestParam(required = false) UUID departmentId,
                                                                        @RequestParam(required = false) ServiceStatus status,
                                                                        @RequestParam(required = false) String category,
                                                                        @RequestParam(required = false) String q,
                                                                        @ParameterObject PageQuery page) {
        var filter = new ManageFilter(departmentId, status, category, q);
        return ApiResponse.of(PageResponse.from(catalogueService.list(filter, page, currentUser.require()),
                ServiceSummaryResponse::from));
    }

    @GetMapping("/{id}/manage")
    @PreAuthorize("hasAuthority('SERVICE:READ_ALL')")
    @Operation(summary = "Get a service working copy with requirements")
    public ApiResponse<ServiceResponse> manageGet(@PathVariable UUID id) {
        return ApiResponse.of(ServiceResponse.from(catalogueService.get(id, currentUser.require())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE:CREATE')")
    @Operation(summary = "Create a draft service in a department in your scope", description = "Supports Idempotency-Key.")
    public ApiResponse<ServiceResponse> create(
            @Valid @RequestBody CreateServiceRequest request,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey) {
        var command = new CreateServiceCommand(request.departmentId(), request.code(), request.name(),
                request.description(), request.category(), request.eligibilityText(), request.processingDays(),
                request.feeAmount(), request.formSchema());
        return ApiResponse.of(catalogueService.create(command, currentUser.require(), idempotencyKey,
                ServiceResponse.class, ServiceResponse::from, ServiceResponse::id).value());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE:UPDATE')")
    @Operation(summary = "Edit a service working copy", description = "Requires the current version. Changes are invisible until published.")
    public ApiResponse<ServiceResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateServiceRequest request) {
        var command = new UpdateServiceCommand(request.name(), request.description(), request.category(),
                request.eligibilityText(), request.processingDays(), request.feeAmount(), request.formSchema(),
                request.version());
        return ApiResponse.of(ServiceResponse.from(catalogueService.update(id, command, currentUser.require())));
    }

    @PostMapping("/{id}/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE:UPDATE')")
    @Operation(summary = "Add a requirement to a service working copy")
    public ApiResponse<RequirementResponse> addRequirement(@PathVariable UUID id,
                                                           @Valid @RequestBody CreateRequirementRequest request) {
        var command = new RequirementCommand(request.code(), request.name(), request.description(),
                request.requirementType(), request.mandatory(), request.allowedMediaTypes(), request.maxSizeBytes(),
                request.displayOrder());
        return ApiResponse.of(RequirementResponse.from(catalogueService.addRequirement(id, command, currentUser.require())));
    }

    @PatchMapping("/{id}/requirements/{requirementId}")
    @PreAuthorize("hasAuthority('SERVICE:UPDATE')")
    @Operation(summary = "Update a requirement of a service working copy")
    public ApiResponse<RequirementResponse> updateRequirement(@PathVariable UUID id, @PathVariable UUID requirementId,
                                                              @Valid @RequestBody UpdateRequirementRequest request) {
        var command = new RequirementCommand(null, request.name(), request.description(), null, request.mandatory(),
                request.allowedMediaTypes(), request.maxSizeBytes(), request.displayOrder());
        return ApiResponse.of(RequirementResponse.from(
                catalogueService.updateRequirement(id, requirementId, command, currentUser.require())));
    }

    @DeleteMapping("/{id}/requirements/{requirementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE:UPDATE')")
    @Operation(summary = "Remove a requirement from a service working copy")
    public void removeRequirement(@PathVariable UUID id, @PathVariable UUID requirementId) {
        catalogueService.removeRequirement(id, requirementId, currentUser.require());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('SERVICE:PUBLISH')")
    @Operation(summary = "Publish the working copy as a new immutable version")
    public ApiResponse<ServiceVersionResponse> publish(@PathVariable UUID id,
                                                       @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.of(ServiceVersionResponse.from(catalogueService.publish(id,
                request == null ? null : request.version(), currentUser.require())));
    }

    @PostMapping("/{id}/deprecate")
    @PreAuthorize("hasAuthority('SERVICE:PUBLISH')")
    @Operation(summary = "Withdraw a published service from the public catalogue")
    public ApiResponse<ServiceSummaryResponse> deprecate(@PathVariable UUID id) {
        return ApiResponse.of(ServiceSummaryResponse.from(catalogueService.deprecate(id, currentUser.require())));
    }
}
