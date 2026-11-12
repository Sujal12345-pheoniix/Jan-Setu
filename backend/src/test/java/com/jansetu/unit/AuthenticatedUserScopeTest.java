package com.jansetu.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.security.AuthenticatedUser;
import com.jansetu.security.Permissions;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserScopeTest {

    private final UUID own = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void departmentMemberCanOnlyAccessOwnDepartment() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "a@example.test", "STAFF", Set.of("OFFICER"),
                Set.of(Permissions.DEPARTMENT_READ), Set.of(own));
        assertThat(user.canAccessDepartment(own)).isTrue();
        assertThat(user.canAccessDepartment(other)).isFalse();
        assertThat(user.canAccessDepartment(null)).isFalse();
    }

    @Test
    void outOfScopeAccessIsReportedAsNotFound() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "a@example.test", "STAFF", Set.of(), Set.of(), Set.of(own));
        assertThatThrownBy(() -> user.requireDepartmentScope(other, "Department"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatCode(() -> user.requireDepartmentScope(own, "Department")).doesNotThrowAnyException();
    }

    @Test
    void globalScopePermissionGrantsAllDepartments() {
        var admin = new AuthenticatedUser(UUID.randomUUID(), "p@example.test", "STAFF", Set.of("PLATFORM_ADMIN"),
                Set.of(Permissions.SCOPE_ALL_DEPARTMENTS), Set.of());
        assertThat(admin.canAccessDepartment(other)).isTrue();
    }

    @Test
    void roleNameAloneDoesNotGrantScope() {
        var fake = new AuthenticatedUser(UUID.randomUUID(), "f@example.test", "STAFF", Set.of("PLATFORM_ADMIN"),
                Set.of(), Set.of());
        assertThat(fake.canAccessDepartment(other)).isFalse();
        assertThatThrownBy(() -> fake.requirePermission(Permissions.USER_CREATE)).isInstanceOf(ApiException.class);
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        var permissions = new java.util.HashSet<>(Set.of(Permissions.USER_READ));
        var user = new AuthenticatedUser(UUID.randomUUID(), "c@example.test", "STAFF", Set.of(), permissions, Set.of());
        permissions.add(Permissions.SCOPE_ALL_DEPARTMENTS);
        assertThat(user.hasGlobalScope()).isFalse();
    }
}
