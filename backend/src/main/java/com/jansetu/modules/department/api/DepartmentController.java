package com.jansetu.modules.department.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.department.api.DepartmentDtos.CreateDepartmentRequest;
import com.jansetu.modules.department.api.DepartmentDtos.DepartmentResponse;
import com.jansetu.modules.department.api.DepartmentDtos.UpdateDepartmentRequest;
import com.jansetu.modules.department.domain.DepartmentStatus;
import com.jansetu.modules.department.service.DepartmentService;
import com.jansetu.modules.department.service.DepartmentService.CreateDepartmentCommand;
import com.jansetu.modules.department.service.DepartmentService.DepartmentFilter;
import com.jansetu.modules.department.service.DepartmentService.UpdateDepartmentCommand;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/departments")
@Tag(name = "Departments", description = "Department management with department-level data isolation")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final CurrentUser currentUser;

    public DepartmentController(DepartmentService departmentService, CurrentUser currentUser) {
        this.departmentService = departmentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DEPARTMENT:READ')")
    @Operation(summary = "List departments in your scope", description = "Sortable by code, name, status, createdAt.")
    public ApiResponse<PageResponse<DepartmentResponse>> list(@RequestParam(required = false) String q,
                                                              @RequestParam(required = false) DepartmentStatus status,
                                                              @ParameterObject PageQuery page) {
        return ApiResponse.of(PageResponse.from(
                departmentService.list(new DepartmentFilter(q, status), page, currentUser.require()),
                DepartmentResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT:READ')")
    @Operation(summary = "Get a department (404 if outside your scope)")
    public ApiResponse<DepartmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(DepartmentResponse.from(departmentService.get(id, currentUser.require())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DEPARTMENT:CREATE')")
    @Operation(summary = "Create a department", description = "Supports the Idempotency-Key header.")
    public ApiResponse<DepartmentResponse> create(
            @Valid @RequestBody CreateDepartmentRequest request,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey) {
        var command = new CreateDepartmentCommand(request.code(), request.name(), request.description(),
                request.contactEmail());
        return ApiResponse.of(departmentService.create(command, currentUser.require(), idempotencyKey,
                DepartmentResponse.class, DepartmentResponse::from, DepartmentResponse::id).value());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT:UPDATE')")
    @Operation(summary = "Update a department in your scope", description = "Requires the current version (optimistic locking).")
    public ApiResponse<DepartmentResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        var command = new UpdateDepartmentCommand(request.name(), request.description(), request.contactEmail(),
                request.version());
        return ApiResponse.of(DepartmentResponse.from(departmentService.update(id, command, currentUser.require())));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('DEPARTMENT:CREATE')")
    @Operation(summary = "Deactivate a department (platform level)")
    public ApiResponse<DepartmentResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.of(DepartmentResponse.from(departmentService.changeStatus(id, false, currentUser.require())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('DEPARTMENT:CREATE')")
    @Operation(summary = "Reactivate a department (platform level)")
    public ApiResponse<DepartmentResponse> activate(@PathVariable UUID id) {
        return ApiResponse.of(DepartmentResponse.from(departmentService.changeStatus(id, true, currentUser.require())));
    }
}
