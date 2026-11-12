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

    public static final int EXPORT_LIMIT = 5000;

    /** Bounded CSV export of matching audit records (newest first). The export itself is audited. */
    @Transactional
    public String exportCsv(AuditFilter filter) {
        List<AuditLog> rows = repository.findAll(toSpecification(filter),
                org.springframework.data.domain.PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .getContent();
        StringBuilder csv = new StringBuilder("occurredAt,actorId,actorType,action,resourceType,resourceId,outcome,requestId\n");
        for (AuditLog r : rows) {
            csv.append(String.join(",", cell(r.getOccurredAt()), cell(r.getActorId()), cell(r.getActorType()),
                    cell(r.getAction()), cell(r.getResourceType()), cell(r.getResourceId()), cell(r.getOutcome()),
                    cell(r.getRequestId()))).append('\n');
        }
        auditService.record(AuditEvent.success("AUDIT_LOG_EXPORTED", "AUDIT_LOG", null).detail("rows", rows.size()));
        return csv.toString();
    }

    /** The signed-in user's own recent account activity (limited, newest first). */
    @Transactional(readOnly = true)
    public List<AuditLog> recentForActor(UUID actorId, int limit) {
        return repository.findAll((root, query, cb) -> cb.equal(root.get("actorId"), actorId),
                org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 100),
                        Sort.by(Sort.Direction.DESC, "occurredAt"))).getContent();
    }

    /** CSV cell with quoting and spreadsheet formula-injection protection. */
    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
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
