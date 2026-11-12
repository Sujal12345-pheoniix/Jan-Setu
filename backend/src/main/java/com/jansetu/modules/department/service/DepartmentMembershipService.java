package com.jansetu.modules.department.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.department.domain.DepartmentMembership;
import com.jansetu.modules.department.repository.DepartmentMembershipRepository;
import com.jansetu.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the department module for staff membership. Callers (the identity module) are responsible for
 * validating that the user is an active staff account; this service enforces department scope and uniqueness.
 */
@Service
public class DepartmentMembershipService {

    public record MembershipView(UUID departmentId, UUID userId, String position, Instant joinedAt) {
    }

    private final DepartmentMembershipRepository repository;
    private final DepartmentService departmentService;
    private final AuditService auditService;
    private final Clock clock;

    public DepartmentMembershipService(DepartmentMembershipRepository repository, DepartmentService departmentService,
                                       AuditService auditService, Clock clock) {
        this.repository = repository;
        this.departmentService = departmentService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Set<UUID> departmentIdsOf(UUID userId) {
        return new HashSet<>(repository.findDepartmentIdsByUserId(userId));
    }

    /** Bulk variant for list views (avoids one query per user). */
    @Transactional(readOnly = true)
    public Map<UUID, Set<UUID>> departmentIdsOf(Collection<UUID> userIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (Object[] row : repository.findMembershipPairsByUserIds(userIds)) {
            result.computeIfAbsent((UUID) row[0], k -> new HashSet<>()).add((UUID) row[1]);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Set<UUID> userIdsIn(Collection<UUID> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(repository.findUserIdsByDepartmentIds(departmentIds));
    }

    @Transactional(readOnly = true)
    public Page<MembershipView> members(UUID departmentId, Pageable pageable, AuthenticatedUser actor) {
        departmentService.get(departmentId, actor); // existence + scope
        return repository.findByDepartmentId(departmentId, pageable).map(DepartmentMembershipService::view);
    }

    @Transactional
    public MembershipView addMember(UUID departmentId, UUID userId, String position, AuthenticatedUser actor) {
        departmentService.get(departmentId, actor);
        departmentService.requireActive(departmentId);
        if (repository.existsByDepartmentIdAndUserId(departmentId, userId)) {
            throw ApiException.duplicate("The user is already a member of this department.");
        }
        DepartmentMembership membership = repository.saveAndFlush(new DepartmentMembership(departmentId, userId,
                position == null || position.isBlank() ? null : position.trim(), clock.instant(), actor.id()));
        auditService.record(AuditEvent.success(AuditActions.DEPARTMENT_MEMBER_ADDED, "DEPARTMENT", departmentId)
                .detail("userId", userId.toString()));
        return view(membership);
    }

    @Transactional
    public void removeMember(UUID departmentId, UUID userId, AuthenticatedUser actor) {
        departmentService.get(departmentId, actor);
        DepartmentMembership membership = repository.findByDepartmentIdAndUserId(departmentId, userId)
                .orElseThrow(() -> ApiException.notFound("Department membership"));
        repository.delete(membership);
        auditService.record(AuditEvent.success(AuditActions.DEPARTMENT_MEMBER_REMOVED, "DEPARTMENT", departmentId)
                .detail("userId", userId.toString()));
    }

    private static MembershipView view(DepartmentMembership m) {
        return new MembershipView(m.getDepartmentId(), m.getUserId(), m.getPosition(), m.getJoinedAt());
    }
}
