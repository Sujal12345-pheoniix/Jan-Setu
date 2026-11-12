package com.jansetu.security;

import com.jansetu.common.error.ApiException;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated actor of a request, resolved from the database on every request.
 *
 * @param id            user id; {@code null} only for the internal system actor
 * @param roles         role codes
 * @param permissions   permission codes (RESOURCE:ACTION)
 * @param departmentIds departments the user is a member of (the user's data scope)
 */
public record AuthenticatedUser(UUID id, String email, String userType, Set<String> roles, Set<String> permissions,
                                Set<UUID> departmentIds) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        departmentIds = Set.copyOf(departmentIds);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** True when department-scoped permissions apply to all departments. */
    public boolean hasGlobalScope() {
        return permissions.contains(Permissions.SCOPE_ALL_DEPARTMENTS);
    }

    public boolean canAccessDepartment(UUID departmentId) {
        return departmentId != null && (hasGlobalScope() || departmentIds.contains(departmentId));
    }

    /**
     * Enforces department isolation. Out-of-scope records are reported as "not found" so their existence is not
     * revealed to other departments.
     */
    public void requireDepartmentScope(UUID departmentId, String resourceName) {
        if (!canAccessDepartment(departmentId)) {
            throw ApiException.notFound(resourceName);
        }
    }

    public void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw ApiException.accessDenied("You do not have permission to perform this action.");
        }
    }

    public boolean isSystem() {
        return id == null;
    }

    /** Internal actor used by trusted startup jobs (e.g. local seed data). Never bound to an HTTP request. */
    public static AuthenticatedUser system(Set<String> allPermissions) {
        return new AuthenticatedUser(null, "system@jansetu.internal", "SYSTEM", Set.of("SYSTEM"), allPermissions, Set.of());
    }
}
