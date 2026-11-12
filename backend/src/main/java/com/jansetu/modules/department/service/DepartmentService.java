package com.jansetu.modules.department.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.department.domain.Department;
import com.jansetu.modules.department.domain.DepartmentStatus;
import com.jansetu.modules.department.repository.DepartmentRepository;
import com.jansetu.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department management. Platform-scoped actors see every department; everyone else only the departments they
 * belong to (department-level data isolation).
 */
@Service
public class DepartmentService {

    private static final Map<String, String> SORTABLE = Map.of(
            "code", "code", "name", "name", "createdAt", "createdAt", "status", "status");

    /** Read model exposed to other modules. */
    public record DepartmentSummary(UUID id, String code, String name, boolean active) {
    }

    public record DepartmentFilter(String q, DepartmentStatus status) {
    }

    public record CreateDepartmentCommand(String code, String name, String description, String contactEmail) {
    }

    /** Partial update. A null field means "unchanged"; {@code version} is the version the client last saw. */
    public record UpdateDepartmentCommand(String name, String description, String contactEmail, Long version) {
    }

    private final DepartmentRepository repository;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    public DepartmentService(DepartmentRepository repository, AuditService auditService,
                             IdempotencyService idempotencyService) {
        this.repository = repository;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional(readOnly = true)
    public Page<Department> list(DepartmentFilter filter, PageQuery pageQuery, AuthenticatedUser actor) {
        return repository.findAll(specification(filter, actor), pageQuery.toPageable(SORTABLE, Sort.by("name")));
    }

    @Transactional(readOnly = true)
    public Department get(UUID id, AuthenticatedUser actor) {
        actor.requireDepartmentScope(id, "Department");
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
    }

    /** Creates a department. Returns the created department mapped by {@code mapper} (also used for idempotent replay). */
    @Transactional
    public <R> IdempotencyService.Result<R> create(CreateDepartmentCommand command, AuthenticatedUser actor,
                                                   String idempotencyKey, Class<R> responseType,
                                                   Function<Department, R> mapper, Function<R, Object> idOf) {
        return idempotencyService.execute(actor.id(), "DEPARTMENT_CREATE", idempotencyKey, command, responseType, idOf,
                () -> mapper.apply(doCreate(command)));
    }

    private Department doCreate(CreateDepartmentCommand command) {
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        String name = command.name().trim();
        if (repository.existsByCode(code)) {
            throw ApiException.duplicate("A department with code " + code + " already exists.");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw ApiException.duplicate("A department with this name already exists.");
        }
        Department department = repository.saveAndFlush(new Department(code, name, blankToNull(command.description()),
                normalizeEmail(command.contactEmail())));
        auditService.record(AuditEvent.success(AuditActions.DEPARTMENT_CREATED, "DEPARTMENT", department.getId())
                .detail("code", code));
        return department;
    }

    @Transactional
    public Department update(UUID id, UpdateDepartmentCommand command, AuthenticatedUser actor) {
        Department department = get(id, actor);
        if (!department.isVersion(command.version())) {
            throw ApiException.staleVersion();
        }
        Map<String, Object> changed = new LinkedHashMap<>();
        if (command.name() != null && !command.name().trim().equals(department.getName())) {
            String name = command.name().trim();
            if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw ApiException.duplicate("A department with this name already exists.");
            }
            department.rename(name);
            changed.put("name", name);
        }
        if (command.description() != null && !Objects.equals(blankToNull(command.description()), department.getDescription())) {
            department.describe(blankToNull(command.description()));
            changed.put("description", "changed");
        }
        if (command.contactEmail() != null
                && !Objects.equals(normalizeEmail(command.contactEmail()), department.getContactEmail())) {
            department.changeContactEmail(normalizeEmail(command.contactEmail()));
            changed.put("contactEmail", "changed");
        }
        if (!changed.isEmpty()) {
            repository.saveAndFlush(department);
            auditService.record(AuditEvent.success(AuditActions.DEPARTMENT_UPDATED, "DEPARTMENT", id)
                    .detail("changes", changed));
        }
        return department;
    }

    @Transactional
    public Department changeStatus(UUID id, boolean active, AuthenticatedUser actor) {
        Department department = get(id, actor);
        if (active) {
            department.activate();
        } else {
            department.deactivate();
        }
        repository.saveAndFlush(department);
        auditService.record(AuditEvent.success(AuditActions.DEPARTMENT_STATUS_CHANGED, "DEPARTMENT", id)
                .detail("status", department.getStatus().name()));
        return department;
    }

    /** Active departments (id, code, name only) for public pickers such as catalogue filters and grievance forms. */
    @Transactional(readOnly = true)
    public List<DepartmentSummary> activeSummaries() {
        return repository.findAll(Sort.by("name")).stream().filter(Department::isActive)
                .map(DepartmentService::summary).toList();
    }

    // ---- Public read API for other modules -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DepartmentSummary requireActive(UUID id) {
        Department department = repository.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
        if (!department.isActive()) {
            throw ApiException.businessRule("Department " + department.getCode() + " is inactive.");
        }
        return summary(department);
    }

    @Transactional(readOnly = true)
    public Map<UUID, DepartmentSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Department::getId, DepartmentService::summary));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<DepartmentSummary> findByCode(String code) {
        return repository.findByCode(code).map(DepartmentService::summary);
    }

    private static DepartmentSummary summary(Department d) {
        return new DepartmentSummary(d.getId(), d.getCode(), d.getName(), d.isActive());
    }

    private static Specification<Department> specification(DepartmentFilter filter, AuthenticatedUser actor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.hasGlobalScope()) {
                if (actor.departmentIds().isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("id").in(actor.departmentIds()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + filter.q().trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("code")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
