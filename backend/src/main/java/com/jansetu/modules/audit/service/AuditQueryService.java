package com.jansetu.modules.audit.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.modules.audit.domain.AuditLog;
import com.jansetu.modules.audit.domain.AuditOutcome;
import com.jansetu.modules.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private static final Map<String, String> SORTABLE = Map.of("occurredAt", "occurredAt", "action", "action");

    public record AuditFilter(UUID actorId, String action, String resourceType, String resourceId,
                              AuditOutcome outcome, Instant from, Instant to) {
    }

    private final AuditLogRepository repository;
    private final AuditService auditService;

    public AuditQueryService(AuditLogRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /** Searches audit records. Access to the audit log is itself audited. */
    @Transactional
    public Page<AuditLog> search(AuditFilter filter, PageQuery pageQuery) {
        Page<AuditLog> page = repository.findAll(toSpecification(filter),
                pageQuery.toPageable(SORTABLE, Sort.by(Sort.Direction.DESC, "occurredAt")));
        auditService.record(AuditEvent.success(AuditActions.AUDIT_LOG_VIEWED, "AUDIT_LOG", null)
                .detail("resultCount", page.getNumberOfElements()));
        return page;
    }

    private static Specification<AuditLog> toSpecification(AuditFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.actorId() != null) {
                predicates.add(cb.equal(root.get("actorId"), f.actorId()));
            }
            if (f.action() != null) {
                predicates.add(cb.equal(root.get("action"), f.action()));
            }
            if (f.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), f.resourceType()));
            }
            if (f.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), f.resourceId()));
            }
            if (f.outcome() != null) {
                predicates.add(cb.equal(root.get("outcome"), f.outcome()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), f.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
