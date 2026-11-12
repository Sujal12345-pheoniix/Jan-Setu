package com.jansetu.modules.audit.service;

import com.jansetu.common.web.ClientInfo;
import com.jansetu.common.web.RequestIds;
import com.jansetu.modules.audit.domain.ActorType;
import com.jansetu.modules.audit.domain.AuditLog;
import com.jansetu.modules.audit.repository.AuditLogRepository;
import com.jansetu.security.CurrentUser;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records audit events. Public API of the audit module for all other modules. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser, Clock clock) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Records within the caller's transaction: the audit record commits or rolls back with the business change. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEvent event) {
        repository.save(toEntity(event));
    }

    /** Records in an independent transaction: used for security events that must persist even if the caller fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEvent event) {
        repository.save(toEntity(event));
    }

    private AuditLog toEntity(AuditEvent event) {
        UUID actorId = event.actorId();
        ActorType actorType;
        if (actorId != null) {
            actorType = ActorType.USER;
        } else {
            var actor = currentUser.find();
            if (actor.isPresent() && !actor.get().isSystem()) {
                actorId = actor.get().id();
                actorType = ActorType.USER;
            } else if (actor.isPresent()) {
                actorType = ActorType.SYSTEM;
            } else {
                actorType = ActorType.ANONYMOUS;
            }
        }
        ClientInfo client = ClientInfo.current();
        return new AuditLog(clock.instant(), actorId, actorType, event.action(), event.resourceType(),
                event.resourceId(), event.outcome(), RequestIds.current(), client.ipAddress(), client.userAgent(),
                event.details());
    }
}
