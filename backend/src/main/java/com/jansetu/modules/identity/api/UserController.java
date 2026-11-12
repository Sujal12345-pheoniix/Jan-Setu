package com.jansetu.modules.identity.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.identity.api.IdentityDtos.CreateUserRequest;
import com.jansetu.modules.identity.api.IdentityDtos.ReplaceRolesRequest;
import com.jansetu.modules.identity.api.IdentityDtos.StatusChangeRequest;
import com.jansetu.modules.identity.api.IdentityDtos.UpdateUserRequest;
import com.jansetu.modules.identity.api.IdentityDtos.UserResponse;
import com.jansetu.modules.identity.domain.UserStatus;
import com.jansetu.modules.identity.domain.UserType;
import com.jansetu.modules.identity.service.UserAdminService;
import com.jansetu.modules.identity.service.UserAdminService.CreateStaffCommand;
import com.jansetu.modules.identity.service.UserAdminService.UpdateUserCommand;
import com.jansetu.modules.identity.service.UserAdminService.UserFilter;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration (scoped by department for non-platform actors)")
public class UserController {

    private final UserAdminService userAdminService;
    private final CurrentUser currentUser;

    public UserController(UserAdminService userAdminService, CurrentUser currentUser) {
        this.userAdminService = userAdminService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER:READ')")
    @Operation(summary = "List users in your scope", description = "Sortable by email, fullName, status, createdAt, lastLoginAt.")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) UserStatus status,
                                                        @RequestParam(required = false) UserType userType,
                                                        @RequestParam(required = false) UUID departmentId,
                                                        @ParameterObject PageQuery page) {
        var filter = new UserFilter(q, status, userType, departmentId);
        return ApiResponse.of(PageResponse.from(userAdminService.list(filter, page, currentUser.require()),
                UserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER:READ')")
    @Operation(summary = "Get a user (404 if outside your scope)")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(UserResponse.from(userAdminService.get(id, currentUser.require())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER:CREATE')")
    @Operation(summary = "Create a staff account",
            description = "The user must change the initial password. Supports the Idempotency-Key header.")
    public ApiResponse<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey) {
        var command = new CreateStaffCommand(request.email(), request.fullName(), request.phone(),
                request.initialPassword(), request.roles());
        return ApiResponse.of(userAdminService.createStaff(command, currentUser.require(), idempotencyKey,
                UserResponse.class, UserResponse::from, UserResponse::id).value());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    @Operation(summary = "Update a user's profile data", description = "Requires the current version.")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.of(UserResponse.from(userAdminService.update(id,
                new UpdateUserCommand(request.fullName(), request.phone(), request.version()), currentUser.require())));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('USER:DEACTIVATE')")
    @Operation(summary = "Suspend an account (revokes all sessions immediately)")
    public ApiResponse<UserResponse> suspend(@PathVariable UUID id, @Valid @RequestBody(required = false) StatusChangeRequest request) {
        return changeStatus(id, UserStatus.SUSPENDED, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER:DEACTIVATE')")
    @Operation(summary = "Deactivate an account (revokes all sessions immediately)")
    public ApiResponse<UserResponse> deactivate(@PathVariable UUID id, @Valid @RequestBody(required = false) StatusChangeRequest request) {
        return changeStatus(id, UserStatus.DEACTIVATED, request);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('USER:DEACTIVATE')")
    @Operation(summary = "Reactivate a suspended or deactivated account")
    public ApiResponse<UserResponse> reactivate(@PathVariable UUID id, @Valid @RequestBody(required = false) StatusChangeRequest request) {
        return changeStatus(id, UserStatus.ACTIVE, request);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER:ASSIGN_ROLE')")
    @Operation(summary = "Replace a user's roles",
            description = "You cannot change your own roles or grant permissions you do not hold.")
    public ApiResponse<UserResponse> replaceRoles(@PathVariable UUID id, @Valid @RequestBody ReplaceRolesRequest request) {
        return ApiResponse.of(UserResponse.from(userAdminService.replaceRoles(id, request.roles(), currentUser.require())));
    }

    private ApiResponse<UserResponse> changeStatus(UUID id, UserStatus status, StatusChangeRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.of(UserResponse.from(userAdminService.changeStatus(id, status, reason, currentUser.require())));
    }
}
