package com.jansetu.modules.identity.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.department.service.DepartmentMembershipService;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.domain.UserType;
import com.jansetu.modules.identity.repository.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only user lookups and assignment-eligibility checks for case-handling modules. */
@Service
public class UserDirectoryService {

    public record UserSummary(UUID id, String fullName, String email) {
    }

    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignments;
    private final DepartmentMembershipService memberships;

    public UserDirectoryService(UserRepository userRepository, RoleAssignmentService roleAssignments,
                                DepartmentMembershipService memberships) {
        this.userRepository = userRepository;
        this.roleAssignments = roleAssignments;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids.stream().filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .map(u -> new UserSummary(u.getId(), u.getFullName(), u.getEmail()))
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    /**
     * Ensures a user may be assigned work in a department: an active staff account, a member of the department,
     * holding the permission the work requires.
     */
    @Transactional(readOnly = true)
    public UserSummary requireEligibleAssignee(UUID userId, UUID departmentId, String requiredPermission) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getUserType() == UserType.STAFF && u.isActive())
                .orElseThrow(() -> ApiException.businessRule("The assignee must be an active staff account."));
        if (!memberships.departmentIdsOf(userId).contains(departmentId)) {
            throw ApiException.businessRule("The assignee is not a member of this department.");
        }
        if (!roleAssignments.permissionCodesOf(userId).contains(requiredPermission)) {
            throw ApiException.businessRule("The assignee does not hold the permission " + requiredPermission + ".");
        }
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
