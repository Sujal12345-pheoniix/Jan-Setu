package com.jansetu.modules.identity.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.modules.department.service.DepartmentMembershipService;
import com.jansetu.modules.department.service.DepartmentMembershipService.MembershipView;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department staff management. Lives in the identity module because it combines user data with memberships:
 * identity depends on the department module, never the other way round.
 */
@Service
public class StaffMembershipService {

    public record MemberView(UUID userId, String email, String fullName, String status, String position,
                             Instant joinedAt, java.util.Set<String> roles) {
    }

    private static final Map<String, String> SORTABLE = Map.of("joinedAt", "joinedAt");

    private final DepartmentMembershipService memberships;
    private final UserAdminService userAdminService;
    private final UserRepository userRepository;
    private final com.jansetu.modules.access.service.RoleAssignmentService roleAssignments;

    public StaffMembershipService(DepartmentMembershipService memberships, UserAdminService userAdminService,
                                  UserRepository userRepository,
                                  com.jansetu.modules.access.service.RoleAssignmentService roleAssignments) {
        this.memberships = memberships;
        this.userAdminService = userAdminService;
        this.userRepository = userRepository;
        this.roleAssignments = roleAssignments;
    }

    @Transactional(readOnly = true)
    public Page<MemberView> members(UUID departmentId, PageQuery pageQuery, AuthenticatedUser actor) {
        Page<MembershipView> page = memberships.members(departmentId,
                pageQuery.toPageable(SORTABLE, Sort.by(Sort.Direction.ASC, "joinedAt")), actor);
        var ids = page.getContent().stream().map(MembershipView::userId).toList();
        Map<UUID, User> users = userRepository.findAllById(ids)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, java.util.Set<String>> roles = roleAssignments.roleCodesOf(ids);
        return page.map(m -> toView(m, users.get(m.userId()), roles.getOrDefault(m.userId(), java.util.Set.of())));
    }

    @Transactional
    public MemberView addMember(UUID departmentId, UUID userId, String position, AuthenticatedUser actor) {
        actor.requireDepartmentScope(departmentId, "Department"); // scope first: no probing of other departments
        User user = userAdminService.requireActiveStaff(userId);
        return toView(memberships.addMember(departmentId, userId, position, actor), user,
                roleAssignments.roleCodesOf(userId));
    }

    @Transactional
    public void removeMember(UUID departmentId, UUID userId, AuthenticatedUser actor) {
        memberships.removeMember(departmentId, userId, actor);
    }

    private static MemberView toView(MembershipView m, User user, java.util.Set<String> roles) {
        return new MemberView(m.userId(), user == null ? null : user.getEmail(), user == null ? null : user.getFullName(),
                user == null ? null : user.getStatus().name(), m.position(), m.joinedAt(), roles);
    }
}
