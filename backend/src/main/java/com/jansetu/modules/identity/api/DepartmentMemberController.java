package com.jansetu.modules.identity.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.modules.identity.api.IdentityDtos.AddMemberRequest;
import com.jansetu.modules.identity.api.IdentityDtos.MemberResponse;
import com.jansetu.modules.identity.service.StaffMembershipService;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments/{departmentId}/members")
@Tag(name = "Departments")
public class DepartmentMemberController {

    private final StaffMembershipService staffMembershipService;
    private final CurrentUser currentUser;

    public DepartmentMemberController(StaffMembershipService staffMembershipService, CurrentUser currentUser) {
        this.staffMembershipService = staffMembershipService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DEPARTMENT:READ')")
    @Operation(summary = "List staff of a department in your scope")
    public ApiResponse<PageResponse<MemberResponse>> list(@PathVariable UUID departmentId, @ParameterObject PageQuery page) {
        return ApiResponse.of(PageResponse.from(
                staffMembershipService.members(departmentId, page, currentUser.require()), MemberResponse::from));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DEPARTMENT:MANAGE_MEMBERS')")
    @Operation(summary = "Add an active staff account to a department in your scope")
    public ApiResponse<MemberResponse> add(@PathVariable UUID departmentId, @Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.of(MemberResponse.from(staffMembershipService.addMember(departmentId, request.userId(),
                request.position(), currentUser.require())));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DEPARTMENT:MANAGE_MEMBERS')")
    @Operation(summary = "Remove a staff member from a department in your scope")
    public void remove(@PathVariable UUID departmentId, @PathVariable UUID userId) {
        staffMembershipService.removeMember(departmentId, userId, currentUser.require());
    }
}
