package com.jansetu.modules.identity.service;

import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.access.service.RoleService;
import com.jansetu.security.AuthenticatedUser;
import com.jansetu.security.UserAuthenticationToken;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * One-time creation of the first platform administrator in any environment.
 *
 * <p>Runs only when {@code BOOTSTRAP_ADMIN_EMAIL} and {@code BOOTSTRAP_ADMIN_PASSWORD} are set AND no user holds the
 * PLATFORM_ADMIN role yet, so it can never create a second admin or reset an existing account. The account must
 * change its password at first sign-in; remove the variables after the first successful start.
 */
@Component
@Order(0)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAdminService userAdminService;
    private final RoleAssignmentService roleAssignments;
    private final RoleService roleService;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrap(UserAdminService userAdminService, RoleAssignmentService roleAssignments, RoleService roleService,
                          @Value("${jansetu.bootstrap.admin-email:}") String email,
                          @Value("${jansetu.bootstrap.admin-password:}") String password,
                          @Value("${jansetu.bootstrap.admin-name:Platform Administrator}") String fullName) {
        this.userAdminService = userAdminService;
        this.roleAssignments = roleAssignments;
        this.roleService = roleService;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (roleAssignments.countUsersWithRole("PLATFORM_ADMIN") > 0) {
            log.info("Admin bootstrap skipped: a platform administrator already exists. Remove BOOTSTRAP_ADMIN_* variables.");
            return;
        }
        Set<String> all = roleService.listPermissions().stream().map(p -> p.getCode()).collect(Collectors.toSet());
        AuthenticatedUser system = AuthenticatedUser.system(all);
        SecurityContextHolder.getContext().setAuthentication(new UserAuthenticationToken(system));
        try {
            userAdminService.createStaff(new UserAdminService.CreateStaffCommand(email, fullName, null, password,
                    Set.of("PLATFORM_ADMIN")), system, null, UserView.class, v -> v, UserView::id);
            log.warn("Bootstrap platform administrator created. Sign in, change the password, then remove BOOTSTRAP_ADMIN_* variables.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
