package com.jansetu.modules.identity.service;

import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.department.service.DepartmentMembershipService;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.security.AuthenticatedUser;
import com.jansetu.security.PrincipalLoader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the request principal from the database (status, roles, permissions, departments). Three small indexed
 * queries per request; a short-lived cache (Redis) can be introduced once load tests justify it.
 */
@Service
public class IdentityPrincipalLoader implements PrincipalLoader {

    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignments;
    private final DepartmentMembershipService memberships;

    public IdentityPrincipalLoader(UserRepository userRepository, RoleAssignmentService roleAssignments,
                                   DepartmentMembershipService memberships) {
        this.userRepository = userRepository;
        this.roleAssignments = roleAssignments;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoadedPrincipal> loadActive(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(user -> new LoadedPrincipal(new AuthenticatedUser(
                        user.getId(),
                        user.getEmail(),
                        user.getUserType().name(),
                        roleAssignments.roleCodesOf(user.getId()),
                        roleAssignments.permissionCodesOf(user.getId()),
                        memberships.departmentIdsOf(user.getId())),
                        user.getPasswordChangedAt()));
    }
}
