package com.jansetu.modules.identity.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.modules.access.service.RoleAssignmentService;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.department.service.DepartmentMembershipService;
import com.jansetu.modules.identity.domain.RefreshSession;
import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service account operations of the signed-in user: profile and device sessions. */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final RoleAssignmentService roleAssignments;
    private final DepartmentMembershipService memberships;
    private final RefreshSessionService refreshSessions;
    private final AuditService auditService;

    public AccountService(UserRepository userRepository, RoleAssignmentService roleAssignments,
                          DepartmentMembershipService memberships, RefreshSessionService refreshSessions,
                          AuditService auditService) {
        this.userRepository = userRepository;
        this.roleAssignments = roleAssignments;
        this.memberships = memberships;
        this.refreshSessions = refreshSessions;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserView me(AuthenticatedUser actor) {
        User user = userRepository.findById(actor.id()).orElseThrow(() -> ApiException.notFound("User"));
        return UserView.of(user, actor.roles(), actor.departmentIds());
    }

    @Transactional
    public UserView updateMe(AuthenticatedUser actor, String fullName, String phone, Long version) {
        User user = userRepository.findById(actor.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.isVersion(version)) {
            throw ApiException.staleVersion();
        }
        user.updateProfile(fullName, phone);
        userRepository.saveAndFlush(user);
        auditService.record(AuditEvent.success(AuditActions.USER_UPDATED, "USER", user.getId()).detail("self", true));
        return UserView.of(user, roleAssignments.roleCodesOf(user.getId()), memberships.departmentIdsOf(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<RefreshSession> sessions(AuthenticatedUser actor) {
        return refreshSessions.activeSessions(actor.id());
    }

    @Transactional
    public void revokeSession(AuthenticatedUser actor, UUID sessionId) {
        RefreshSession session = refreshSessions.findOwned(sessionId, actor.id())
                .orElseThrow(() -> ApiException.notFound("Session"));
        refreshSessions.revokeFamily(session.getFamilyId(), RefreshSessionService.REASON_USER_REVOKED);
        auditService.record(AuditEvent.success(AuditActions.SESSIONS_REVOKED, "USER", actor.id())
                .detail("sessionId", sessionId.toString()));
    }
}
