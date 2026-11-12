package com.jansetu.modules.grievance.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.modules.document.service.DocumentService;
import com.jansetu.modules.document.service.DocumentService.DocumentView;
import com.jansetu.modules.grievance.domain.GrievanceStatus;
import com.jansetu.modules.grievance.service.GrievanceService;
import com.jansetu.modules.grievance.service.GrievanceService.CreateCommand;
import com.jansetu.modules.grievance.service.GrievanceService.Detail;
import com.jansetu.modules.grievance.service.GrievanceService.Filter;
import com.jansetu.modules.grievance.service.GrievanceService.Summary;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/grievances")
@Tag(name = "Grievances", description = "Grievance registration, handling, resolution and reopening")
public class GrievanceController {

    public record CreateRequest(UUID departmentId,
                                @NotBlank @Pattern(regexp = "SERVICE_DELAY|STAFF_CONDUCT|INCORRECT_DECISION|DOCUMENT_ISSUE|PORTAL_ISSUE|OTHER",
                                        message = "must be a supported category") String category,
                                @NotBlank @Size(min = 5, max = 200) String subject,
                                @NotBlank @Size(min = 20, max = 4000) String description,
                                UUID applicationId) {
    }

    public record AssignRequest(@NotNull UUID assigneeId) {
    }

    public record CommentRequest(@NotBlank @Pattern(regexp = "PUBLIC|INTERNAL") String visibility,
                                 @NotBlank @Size(max = 4000) String body) {
    }

    public record TextRequest(@NotBlank @Size(max = 4000) String text) {
    }

    public record FeedbackRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String comment) {
    }

    private final GrievanceService service;
    private final CurrentUser currentUser;

    public GrievanceController(GrievanceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/categories")
    @Operation(summary = "Supported grievance categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.of(GrievanceService.CATEGORIES.stream().sorted().toList());
    }

    @GetMapping
    @Operation(summary = "List grievances (citizens: own; staff: their departments)")
    public ApiResponse<PageResponse<Summary>> list(@RequestParam(required = false) GrievanceStatus status,
                                                   @RequestParam(required = false) String category,
                                                   @RequestParam(required = false) UUID departmentId,
                                                   @RequestParam(defaultValue = "false") boolean assignedToMe,
                                                   @RequestParam(required = false) String q,
                                                   @ParameterObject PageQuery page) {
        return ApiResponse.of(PageResponse.of(service.list(new Filter(status, category, departmentId, assignedToMe, q),
                page, currentUser.require())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Grievance detail (internal notes only for staff)")
    public ApiResponse<Detail> detail(@PathVariable UUID id) {
        return ApiResponse.of(service.detail(id, currentUser.require()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a grievance")
    public ApiResponse<Detail> create(@Valid @RequestBody CreateRequest request) {
        var actor = currentUser.require();
        UUID id = service.create(new CreateCommand(request.departmentId(), request.category(), request.subject(),
                request.description(), request.applicationId()), actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge a submitted grievance (staff)")
    public ApiResponse<Detail> acknowledge(@PathVariable UUID id) {
        var actor = currentUser.require();
        service.acknowledge(id, actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign or reassign (GRIEVANCE:ASSIGN)")
    public ApiResponse<Detail> assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        var actor = currentUser.require();
        service.assign(id, request.assigneeId(), actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start work (assigned officer)")
    public ApiResponse<Detail> start(@PathVariable UUID id) {
        var actor = currentUser.require();
        service.start(id, actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve with a public resolution summary (assigned officer)")
    public ApiResponse<Detail> resolve(@PathVariable UUID id, @Valid @RequestBody TextRequest request) {
        var actor = currentUser.require();
        service.resolve(id, request.text(), actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment (citizen: PUBLIC; staff: PUBLIC reply or INTERNAL note)")
    public ApiResponse<Detail> comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
        var actor = currentUser.require();
        service.comment(id, request.visibility(), request.body(), actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reopen a resolved grievance (citizen, within the reopen window, once)")
    public ApiResponse<Detail> reopen(@PathVariable UUID id, @Valid @RequestBody TextRequest request) {
        var actor = currentUser.require();
        service.reopen(id, request.text(), actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close (citizen accepts the resolution, or staff after the reopen window)")
    public ApiResponse<Detail> close(@PathVariable UUID id) {
        var actor = currentUser.require();
        service.close(id, actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping("/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Rate the resolution (citizen, once)")
    public ApiResponse<Detail> feedback(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest request) {
        var actor = currentUser.require();
        service.submitFeedback(id, request.rating(), request.comment(), actor);
        return ApiResponse.of(service.detail(id, actor));
    }

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload supporting evidence (citizen; PDF/PNG/JPEG)")
    public ApiResponse<DocumentView> upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        try {
            return ApiResponse.of(service.uploadEvidence(id, new DocumentService.UploadCommand(file.getOriginalFilename(),
                    file.getContentType(), file.getBytes()), currentUser.require()));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The upload could not be read.");
        }
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @Operation(summary = "Download evidence (owner or in-scope staff)")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id, @PathVariable UUID documentId) {
        DocumentService.Download d = service.openEvidence(id, documentId, currentUser.require());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mediaType()))
                .contentLength(d.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(d.content()));
    }
}
