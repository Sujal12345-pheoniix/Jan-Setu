package com.jansetu.modules.application.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.application.domain.ApplicationStatus;
import com.jansetu.modules.application.service.ApplicationQueryService;
import com.jansetu.modules.application.service.ApplicationQueryService.ApplicationFilter;
import com.jansetu.modules.application.service.ApplicationService;
import com.jansetu.modules.application.service.ApplicationService.SubmitResult;
import com.jansetu.modules.application.service.ApplicationViews.Detail;
import com.jansetu.modules.application.service.ApplicationViews.Summary;
import com.jansetu.modules.application.service.CaseworkService;
import com.jansetu.modules.document.service.DocumentService;
import com.jansetu.modules.document.service.DocumentService.DocumentView;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Citizen applications and staff case handling")
public class ApplicationController {

    public record CreateDraftRequest(@NotNull UUID serviceId) {
    }

    public record UpdateDraftRequest(@NotNull Map<String, Object> formData, @NotNull Long version) {
    }

    public record ReasonRequest(@Size(max = 2000) String reason) {
    }

    public record RequiredReasonRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    public record ClarificationRequestBody(@NotBlank @Size(max = 2000) String question,
                                           @Min(1) @Max(60) Integer dueInDays) {
    }

    public record ClarificationResponseBody(@NotBlank @Size(max = 4000) String response) {
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record DocumentDecisionRequest(@NotNull Boolean accept, @Size(max = 1000) String reason) {
    }

    private final ApplicationService applicationService;
    private final ApplicationQueryService queryService;
    private final CaseworkService casework;
    private final CurrentUser currentUser;

    public ApplicationController(ApplicationService applicationService, ApplicationQueryService queryService,
                                 CaseworkService casework, CurrentUser currentUser) {
        this.applicationService = applicationService;
        this.queryService = queryService;
        this.casework = casework;
        this.currentUser = currentUser;
    }

    // ---- Queries --------------------------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List applications", description = "Citizens: own applications. Staff: their departments (drafts excluded).")
    public ApiResponse<PageResponse<Summary>> list(@RequestParam(required = false) ApplicationStatus status,
                                                   @RequestParam(required = false) UUID serviceId,
                                                   @RequestParam(required = false) UUID departmentId,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "false") boolean assignedToMe,
                                                   @RequestParam(defaultValue = "false") boolean overdue,
                                                   @ParameterObject PageQuery page) {
        var filter = new ApplicationFilter(status, serviceId, departmentId, q, assignedToMe, overdue);
        return ApiResponse.of(PageResponse.of(queryService.list(filter, page, currentUser.require())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Application detail (404 if not yours / outside your scope)")
    public ApiResponse<Detail> detail(@PathVariable UUID id) {
        return ApiResponse.of(queryService.detail(id, currentUser.require()));
    }

    @GetMapping("/by-reference/{reference}")
    @Operation(summary = "Find an application id by reference number")
    public ApiResponse<Map<String, UUID>> byReference(@PathVariable String reference) {
        return ApiResponse.of(Map.of("id", queryService.idByReference(reference, currentUser.require())));
    }

    // ---- Citizen commands -----------------------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a draft for a published service")
    public ApiResponse<Detail> create(@Valid @RequestBody CreateDraftRequest request) {
        var actor = currentUser.require();
        UUID id = applicationService.createDraft(request.serviceId(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Save draft answers", description = "Requires the current version. Types are checked; required fields only at submission.")
    public ApiResponse<Detail> update(@PathVariable UUID id, @Valid @RequestBody UpdateDraftRequest request) {
        var actor = currentUser.require();
        applicationService.updateDraft(id, request.formData(), request.version(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a draft", description = "Validates answers and documents, assigns a reference number, starts the workflow. Supports Idempotency-Key.")
    public ApiResponse<SubmitResult> submit(@PathVariable UUID id,
                                            @RequestHeader(value = IdempotencyService.HEADER, required = false) String key) {
        return ApiResponse.of(applicationService.submit(id, key, currentUser.require()));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw a draft or a submitted application not yet under review")
    public ApiResponse<Detail> withdraw(@PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest request) {
        var actor = currentUser.require();
        applicationService.withdraw(id, request == null ? null : request.reason(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/clarification-response")
    @Operation(summary = "Respond to an open clarification request")
    public ApiResponse<Detail> respond(@PathVariable UUID id, @Valid @RequestBody ClarificationResponseBody request) {
        var actor = currentUser.require();
        applicationService.respondToClarification(id, request.response(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload or replace the document for a requirement",
            description = "PDF, PNG or JPEG; size and type are validated on the server (magic bytes).")
    public ApiResponse<DocumentView> upload(@PathVariable UUID id, @RequestParam String requirementCode,
                                            @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(applicationService.uploadDocument(id, requirementCode, toUpload(file), currentUser.require()));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @Operation(summary = "Download a document (owner or in-scope staff)")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id, @PathVariable UUID documentId,
                                                        @RequestParam(required = false) Integer version) {
        return fileResponse(queryService.openDocument(id, documentId, version, currentUser.require()));
    }

    // ---- Staff commands -------------------------------------------------------------------------------------

    @PostMapping("/{id}/start-review")
    @Operation(summary = "Claim the review task and start review (TASK:CLAIM)")
    public ApiResponse<Detail> startReview(@PathVariable UUID id) {
        var actor = currentUser.require();
        casework.startReview(id, actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/request-clarification")
    @Operation(summary = "Ask the applicant for more information (assigned officer)")
    public ApiResponse<Detail> requestClarification(@PathVariable UUID id, @Valid @RequestBody ClarificationRequestBody request) {
        var actor = currentUser.require();
        casework.requestClarification(id, request.question(), request.dueInDays(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/recommend")
    @Operation(summary = "Recommend approval (assigned officer; mandatory documents must be verified)")
    public ApiResponse<Detail> recommend(@PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest request) {
        var actor = currentUser.require();
        casework.recommend(id, request == null ? null : request.reason(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve (reviewer; not the recommending officer)")
    public ApiResponse<Detail> approve(@PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest request) {
        var actor = currentUser.require();
        casework.approve(id, request == null ? null : request.reason(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject with a mandatory reason")
    public ApiResponse<Detail> reject(@PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest request) {
        var actor = currentUser.require();
        casework.reject(id, request.reason(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "Return to the reviewing officer for correction (reviewer)")
    public ApiResponse<Detail> returnForCorrection(@PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest request) {
        var actor = currentUser.require();
        casework.returnForCorrection(id, request.reason(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark an approved application as completed")
    public ApiResponse<Detail> complete(@PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest request) {
        var actor = currentUser.require();
        casework.complete(id, request == null ? null : request.reason(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    @PostMapping("/{id}/documents/{documentId}/verification")
    @Operation(summary = "Verify or reject a document (DOCUMENT:VERIFY)")
    public ApiResponse<DocumentView> verifyDocument(@PathVariable UUID id, @PathVariable UUID documentId,
                                                    @Valid @RequestBody DocumentDecisionRequest request) {
        return ApiResponse.of(casework.verifyDocument(id, documentId, request.accept(), request.reason(), currentUser.require()));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an internal comment (never shown to the applicant)")
    public ApiResponse<Detail> comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
        var actor = currentUser.require();
        casework.addComment(id, request.body(), actor);
        return ApiResponse.of(queryService.detail(id, actor));
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    static DocumentService.UploadCommand toUpload(MultipartFile file) {
        try {
            return new DocumentService.UploadCommand(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The upload could not be read.");
        }
    }

    static ResponseEntity<InputStreamResource> fileResponse(DocumentService.Download download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(download.content()));
    }
}
