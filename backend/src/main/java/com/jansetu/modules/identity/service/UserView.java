package com.jansetu.modules.identity.service;

import com.jansetu.modules.identity.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A user together with its roles and department memberships (assembled across modules without N+1 queries). */
public record UserView(UUID id, String email, String fullName, String phone, String userType, String status,
                       String statusReason, boolean passwordChangeRequired, Instant lastLoginAt, Set<String> roles,
                       Set<UUID> departmentIds, Instant createdAt, Instant updatedAt, long version) {

    static UserView of(User user, Set<String> roles, Set<UUID> departmentIds) {
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getUserType().name(), user.getStatus().name(), user.getStatusReason(),
                user.isPasswordChangeRequired(), user.getLastLoginAt(), Set.copyOf(roles), Set.copyOf(departmentIds),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }
}
