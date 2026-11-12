package com.jansetu.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.access.service.RoleService;
import com.jansetu.modules.identity.service.AdminBootstrap;
import com.jansetu.modules.identity.service.UserAdminService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapTest {

    @Mock
    private UserAdminService users;
    @Mock
    private RoleAssignmentService roles;
    @Mock
    private RoleService roleService;

    private AdminBootstrap bootstrap(String email, String password) {
        when(roleService.listPermissions()).thenReturn(List.of());
        return new AdminBootstrap(users, roles, roleService, email, password, "Platform Administrator");
    }

    @Test
    void doesNothingWithoutConfiguration() throws Exception {
        bootstrap("", "").run(null);
        verify(users, never()).createStaff(any(), any(), any(), any(), any(), any());
    }

    @Test
    void neverCreatesASecondAdministrator() throws Exception {
        when(roles.countUsersWithRole("PLATFORM_ADMIN")).thenReturn(1L);
        bootstrap("admin@example.test", "Bootstrap-Pass-2026").run(null);
        verify(users, never()).createStaff(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createsTheFirstPlatformAdministrator() throws Exception {
        when(roles.countUsersWithRole("PLATFORM_ADMIN")).thenReturn(0L);
        bootstrap("admin@example.test", "Bootstrap-Pass-2026").run(null);
        ArgumentCaptor<UserAdminService.CreateStaffCommand> command = ArgumentCaptor.forClass(UserAdminService.CreateStaffCommand.class);
        verify(users).createStaff(command.capture(), any(), isNull(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(command.getValue().roles()).isEqualTo(Set.of("PLATFORM_ADMIN"));
        org.assertj.core.api.Assertions.assertThat(command.getValue().email()).isEqualTo("admin@example.test");
    }
}
