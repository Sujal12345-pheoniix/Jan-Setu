package com.jansetu.modules.identity.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.department.service.DepartmentMembershipService;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.domain.UserStatus;
import com.jansetu.modules.identity.domain.UserType;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of user accounts.
 *
 * <p>Scope: actors with {@code SCOPE:ALL_DEPARTMENTS} manage every account; department administrators only see staff
 * who belong to one of their departments. Out-of-scope accounts are reported as not found.
 */
@Service
public class UserAdminService {

    private static final Map<String, String> SORTABLE = Map.of(
            "email", "emailNormalized", "fullName", "fullName", "createdAt", "createdAt", "status", "status",
            "lastLoginAt", "lastLoginAt");

    public record UserFilter(String q, UserStatus status, UserType userType, UUID departmentId) {
    }

    public record CreateStaffCommand(String email, String fullName, String phone, String initialPassword,
                                     Set<String> roles) {

        /** The idempotency fingerprint must never include the password. */
        CreateStaffCommand withoutPassword() {
            return new CreateStaffCommand(email, fullName, phone, null, roles);
        }
    }

    public record UpdateUserCommand(String fullName, String phone, Long version) {
    }

    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignments;
    private final DepartmentMembershipService memberships;
    private final RefreshSessionService refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final Clock clock;

    public UserAdminService(UserRepository userRepository, RoleAssignmentService roleAssignments,
                            DepartmentMembershipService memberships, RefreshSessionService refreshSessions,
                            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                            IdempotencyService idempotencyService, AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.roleAssignments = roleAssignments;
        this.memberships = memberships;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(UserFilter filter, PageQuery pageQuery, AuthenticatedUser actor) {
        Set<UUID> scopedIds = null;
        if (filter.departmentId() != null) {
            actor.requireDepartmentScope(filter.departmentId(), "Department");
            scopedIds = memberships.userIdsIn(Set.of(filter.departmentId()));
        } else if (!actor.hasGlobalScope()) {
            scopedIds = memberships.userIdsIn(actor.departmentIds());
        }
        if (scopedIds != null && scopedIds.isEmpty()) {
            return Page.empty(pageQuery.toPageable(SORTABLE, Sort.by("fullName")));
        }
        Page<User> users = userRepository.findAll(specification(filter, scopedIds),
                pageQuery.toPageable(SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UUID> ids = users.getContent().stream().map(User::getId).toList();
        Map<UUID, Set<String>> roles = roleAssignments.roleCodesOf(ids);
        Map<UUID, Set<UUID>> departments = memberships.departmentIdsOf(ids);
        return users.map(u -> UserView.of(u, roles.getOrDefault(u.getId(), Set.of()),
                departments.getOrDefault(u.getId(), Set.of())));
    }

    @Transactional(readOnly = true)
    public UserView get(UUID id, AuthenticatedUser actor) {
        return view(loadInScope(id, actor));
    }

    @Transactional
    public <R> IdempotencyService.Result<R> createStaff(CreateStaffCommand command, AuthenticatedUser actor,
                                                        String idempotencyKey, Class<R> responseType,
                                                        Function<UserView, R> mapper, Function<R, Object> idOf) {
        return idempotencyService.execute(actor.id(), "USER_CREATE", idempotencyKey, command.withoutPassword(),
                responseType, idOf, () -> mapper.apply(doCreateStaff(command, actor)));
    }

    private UserView doCreateStaff(CreateStaffCommand command, AuthenticatedUser actor) {
        String normalized = User.normalizeEmail(command.email());
        if (command.roles().contains("CITIZEN")) {
            throw ApiException.businessRule("Staff accounts cannot hold the CITIZEN role.");
        }
        passwordPolicy.validate(command.initialPassword(), normalized);
        if (userRepository.existsByEmailNormalized(normalized)) {
            throw ApiException.duplicate("An account with this email already exists.");
        }
        User user = userRepository.saveAndFlush(new User(command.email(), passwordEncoder.encode(command.initialPassword()),
                command.fullName(), blankToNull(command.phone()), UserType.STAFF, clock.instant(), true));
        Set<String> roles = roleAssignments.replaceRoles(user.getId(), command.roles(), actor);
        auditService.record(AuditEvent.success(AuditActions.USER_CREATED, "USER", user.getId())
                .detail("userType", UserType.STAFF.name())
                .detail("roles", roles));
        return UserView.of(user, roles, Set.of());
    }

    @Transactional
    public UserView update(UUID id, UpdateUserCommand command, AuthenticatedUser actor) {
        User user = loadInScope(id, actor);
        if (!user.isVersion(command.version())) {
            throw ApiException.staleVersion();
        }
        user.updateProfile(command.fullName(), command.phone());
        userRepository.saveAndFlush(user);
        auditService.record(AuditEvent.success(AuditActions.USER_UPDATED, "USER", id));
        return view(user);
    }

    /** Suspends, deactivates or reactivates an account. Leaving ACTIVE revokes every refresh session at once. */
    @Transactional
    public UserView changeStatus(UUID id, UserStatus newStatus, String reason, AuthenticatedUser actor) {
        if (id.equals(actor.id())) {
            throw ApiException.businessRule("You cannot change the status of your own account.");
        }
        User user = loadInScope(id, actor);
        UserStatus previous = user.getStatus();
        user.changeStatus(newStatus, blankToNull(reason));
        userRepository.saveAndFlush(user);
        if (newStatus != UserStatus.ACTIVE) {
            refreshSessions.revokeAll(user.getId(), RefreshSessionService.REASON_ACCOUNT_STATUS);
        }
        auditService.record(AuditEvent.success(AuditActions.USER_STATUS_CHANGED, "USER", id)
                .detail("from", previous.name())
                .detail("to", newStatus.name()));
        return view(user);
    }

    @Transactional
    public UserView replaceRoles(UUID id, Set<String> roleCodes, AuthenticatedUser actor) {
        if (id.equals(actor.id())) {
            throw ApiException.businessRule("You cannot change your own roles.");
        }
        User user = loadInScope(id, actor);
        boolean citizenRole = roleCodes.contains("CITIZEN");
        if (user.getUserType() == UserType.CITIZEN && (roleCodes.size() != 1 || !citizenRole)) {
            throw ApiException.businessRule("Citizen accounts can only hold the CITIZEN role.");
        }
        if (user.getUserType() == UserType.STAFF && citizenRole) {
            throw ApiException.businessRule("Staff accounts cannot hold the CITIZEN role.");
        }
        Set<String> before = roleAssignments.roleCodesOf(id);
        Set<String> after = roleAssignments.replaceRoles(id, roleCodes, actor);
        auditService.record(AuditEvent.success(AuditActions.USER_ROLES_CHANGED, "USER", id)
                .detail("before", before)
                .detail("after", after));
        return UserView.of(user, after, memberships.departmentIdsOf(id));
    }

    // ---- Public API for other identity services --------------------------------------------------------------

    /** Loads an active staff account (used when adding department members). */
    @Transactional(readOnly = true)
    public User requireActiveStaff(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getUserType() != UserType.STAFF) {
            throw ApiException.businessRule("Only staff accounts can be department members.");
        }
        if (!user.isActive()) {
            throw ApiException.businessRule("The account is not active.");
        }
        return user;
    }

    private User loadInScope(UUID id, AuthenticatedUser actor) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (!actor.hasGlobalScope() && !id.equals(actor.id())) {
            Set<UUID> shared = new HashSet<>(memberships.departmentIdsOf(id));
            shared.retainAll(actor.departmentIds());
            if (shared.isEmpty()) {
                throw ApiException.notFound("User");
            }
        }
        return user;
    }

    private UserView view(User user) {
        return UserView.of(user, roleAssignments.roleCodesOf(user.getId()), memberships.departmentIdsOf(user.getId()));
    }

    private static Specification<User> specification(UserFilter filter, Set<UUID> scopedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopedIds != null) {
                predicates.add(root.get("id").in(scopedIds));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.userType() != null) {
                predicates.add(cb.equal(root.get("userType"), filter.userType()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + filter.q().trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(root.get("emailNormalized"), like, '\\'),
                        cb.like(cb.lower(root.get("fullName")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
