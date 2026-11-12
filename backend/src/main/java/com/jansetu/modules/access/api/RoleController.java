package com.jansetu.modules.access.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.modules.access.api.AccessDtos.CreateRoleRequest;
import com.jansetu.modules.access.api.AccessDtos.PermissionResponse;
import com.jansetu.modules.access.api.AccessDtos.ReplacePermissionsRequest;
import com.jansetu.modules.access.api.AccessDtos.RoleResponse;
import com.jansetu.modules.access.api.AccessDtos.RoleSummaryResponse;
import com.jansetu.modules.access.service.RoleService;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Roles and permissions")
public class RoleController {

    private final RoleService roleService;
    private final CurrentUser currentUser;

    public RoleController(RoleService roleService, CurrentUser currentUser) {
        this.roleService = roleService;
        this.currentUser = currentUser;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE:READ')")
    @Operation(summary = "List roles", description = "Sortable by code, name, createdAt.")
    public ApiResponse<PageResponse<RoleSummaryResponse>> list(@ParameterObject PageQuery page) {
        return ApiResponse.of(PageResponse.from(roleService.list(page), RoleSummaryResponse::from));
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE:READ')")
    @Operation(summary = "Get a role with its permissions")
    public ApiResponse<RoleResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(RoleResponse.from(roleService.get(id)));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE:CREATE')")
    @Operation(summary = "Create a custom role", description = "You can only include permissions you hold yourself.")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.of(RoleResponse.from(roleService.create(request.code(), request.name(),
                request.description(), request.permissions(), currentUser.require())));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    @Operation(summary = "Replace the permissions of a custom role", description = "System roles are immutable.")
    public ApiResponse<RoleResponse> replacePermissions(@PathVariable UUID id,
                                                        @Valid @RequestBody ReplacePermissionsRequest request) {
        return ApiResponse.of(RoleResponse.from(roleService.replacePermissions(id, request.permissions(),
                request.version(), currentUser.require())));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION:READ')")
    @Operation(summary = "List the permission catalogue")
    public ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.of(roleService.listPermissions().stream().map(PermissionResponse::from).toList());
    }
}
