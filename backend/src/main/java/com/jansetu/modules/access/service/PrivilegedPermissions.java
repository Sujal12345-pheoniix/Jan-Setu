package com.jansetu.modules.access.service;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Permissions that control access itself (account administration, role management, platform-wide scope, creating
 * departments). An actor may only grant these if they hold them; operational permissions (applications, tasks,
 * grievances, services, reports, audit read) may be granted by any authorised role administrator.
 */
final class PrivilegedPermissions {

    private static final Set<String> PREFIXES = Set.of("USER:", "ROLE:", "PERMISSION:");
    private static final Set<String> CODES = Set.of("SCOPE:ALL_DEPARTMENTS", "DEPARTMENT:CREATE");

    private PrivilegedPermissions() {
    }

    static boolean isPrivileged(String permission) {
        return CODES.contains(permission) || PREFIXES.stream().anyMatch(permission::startsWith);
    }

    /** Privileged permissions in {@code requested} that the actor does not hold. */
    static Set<String> missing(Collection<String> requested, Set<String> actorPermissions) {
        Set<String> missing = new TreeSet<>();
        for (String permission : requested) {
            if (isPrivileged(permission) && !actorPermissions.contains(permission)) {
                missing.add(permission);
            }
        }
        return missing;
    }
}
