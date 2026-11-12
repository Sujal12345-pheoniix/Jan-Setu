package com.jansetu.modules.access.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.modules.access.domain.Permission;
import com.jansetu.modules.access.domain.Role;
import com.jansetu.modules.access.domain.UserRoleAssignment;
import com.jansetu.modules.access.repository.RoleRepository;
import com.jansetu.modules.access.repository.UserRoleAssignmentRepository;
import com.jansetu.security.AuthenticatedUser;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the access module for granting roles and resolving authorities. Includes the privilege
 * escalation guard: an actor can only grant roles whose <em>privileged</em> permissions (account, role and
 * platform-scope administration) they hold themselves. See {@link PrivilegedPermissions}.
 */
@Service
public class RoleAssignmentService {

    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final Clock clock;

    public RoleAssignmentService(RoleRepository roleRepository, UserRoleAssignmentRepository assignmentRepository,
                                 Clock clock) {
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Set<String> roleCodesOf(UUID userId) {
        return new TreeSet<>(assignmentRepository.findRoleCodesByUserId(userId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Set<String>> roleCodesOf(Collection<UUID> userIds) {
        Map<UUID, Set<String>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (Object[] row : assignmentRepository.findRoleCodesByUserIds(userIds)) {
            result.computeIfAbsent((UUID) row[0], k -> new TreeSet<>()).add((String) row[1]);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Set<String> permissionCodesOf(UUID userId) {
        return new HashSet<>(assignmentRepository.findPermissionCodesByUserId(userId));
    }

    /** Number of users holding a role (used by the one-time admin bootstrap). */
    @Transactional(readOnly = true)
    public long countUsersWithRole(String roleCode) {
        return roleRepository.findByCode(roleCode).map(r -> assignmentRepository.countByRoleId(r.getId())).orElse(0L);
    }

    /** Validates role codes and returns the union of their permissions. */
    @Transactional(readOnly = true)
    public Set<String> permissionsOfRoles(Set<String> roleCodes) {
        return resolveRoles(roleCodes).stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    /**
     * Replaces all roles of a user. The actor must hold every permission contained in the new roles, which
     * prevents privilege escalation through custom roles or delegated administration.
     */
    @Transactional
    public Set<String> replaceRoles(UUID userId, Set<String> roleCodes, AuthenticatedUser actor) {
        List<Role> roles = resolveRoles(roleCodes);
        if (!actor.isSystem()) {
            Set<String> requested = roles.stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
            Set<String> missing = PrivilegedPermissions.missing(requested, actor.permissions());
            if (!missing.isEmpty()) {
                throw new ApiException(ErrorCode.PRIVILEGE_ESCALATION,
                        "You cannot grant a role with privileged permissions you do not hold: "
                                + String.join(", ", missing) + ".");
            }
        }
        assignmentRepository.deleteByUserId(userId);
        assignmentRepository.flush();
        var now = clock.instant();
        assignmentRepository.saveAll(roles.stream()
                .map(r -> new UserRoleAssignment(userId, r.getId(), now, actor.id()))
                .toList());
        return roles.stream().map(Role::getCode).collect(Collectors.toCollection(TreeSet::new));
    }

    private List<Role> resolveRoles(Set<String> roleCodes) {
        List<Role> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            Set<String> found = roles.stream().map(Role::getCode).collect(Collectors.toSet());
            Set<String> unknown = new TreeSet<>(roleCodes);
            unknown.removeAll(found);
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown role(s): " + String.join(", ", unknown) + ".");
        }
        return roles;
    }
}
