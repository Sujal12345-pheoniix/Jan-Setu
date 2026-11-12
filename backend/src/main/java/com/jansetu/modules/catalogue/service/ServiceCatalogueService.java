package com.jansetu.modules.catalogue.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.catalogue.domain.RequirementType;
import com.jansetu.modules.catalogue.domain.ServiceDefinition;
import com.jansetu.modules.catalogue.domain.ServiceRequirement;
import com.jansetu.modules.catalogue.domain.ServiceStatus;
import com.jansetu.modules.catalogue.domain.ServiceVersion;
import com.jansetu.modules.catalogue.repository.ServiceDefinitionRepository;
import com.jansetu.modules.catalogue.repository.ServiceVersionRepository;
import com.jansetu.modules.department.service.DepartmentService;
import com.jansetu.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Management of the service catalogue (working copies, requirements, publish/deprecate). All operations are
 * restricted to services of departments in the actor's scope.
 */
@Service
public class ServiceCatalogueService {

    private static final Map<String, String> SORTABLE = Map.of(
            "code", "code", "name", "name", "status", "status", "createdAt", "createdAt", "updatedAt", "updatedAt");

    public record ManageFilter(UUID departmentId, ServiceStatus status, String category, String q) {
    }

    public record CreateServiceCommand(UUID departmentId, String code, String name, String description,
                                       String category, String eligibilityText, int processingDays,
                                       BigDecimal feeAmount, Map<String, Object> formSchema) {
    }

    public record UpdateServiceCommand(String name, String description, String category, String eligibilityText,
                                       Integer processingDays, BigDecimal feeAmount, Map<String, Object> formSchema,
                                       Long version) {
    }

    public record RequirementCommand(String code, String name, String description, RequirementType type,
                                     Boolean mandatory, String allowedMediaTypes, Long maxSizeBytes,
                                     Integer displayOrder) {
    }

    private final ServiceDefinitionRepository serviceRepository;
    private final ServiceVersionRepository versionRepository;
    private final DepartmentService departmentService;
    private final FormSchemaValidator formSchemaValidator;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final Clock clock;

    public ServiceCatalogueService(ServiceDefinitionRepository serviceRepository,
                                   ServiceVersionRepository versionRepository, DepartmentService departmentService,
                                   FormSchemaValidator formSchemaValidator, IdempotencyService idempotencyService,
                                   AuditService auditService, Clock clock) {
        this.serviceRepository = serviceRepository;
        this.versionRepository = versionRepository;
        this.departmentService = departmentService;
        this.formSchemaValidator = formSchemaValidator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ServiceDefinition> list(ManageFilter filter, PageQuery pageQuery, AuthenticatedUser actor) {
        if (filter.departmentId() != null) {
            actor.requireDepartmentScope(filter.departmentId(), "Department");
        }
        return serviceRepository.findAll(specification(filter, actor), pageQuery.toPageable(SORTABLE, Sort.by("name")));
    }

    @Transactional(readOnly = true)
    public ServiceDefinition get(UUID id, AuthenticatedUser actor) {
        ServiceDefinition service = serviceRepository.findWithRequirementsById(id)
                .orElseThrow(() -> ApiException.notFound("Service"));
        actor.requireDepartmentScope(service.getDepartmentId(), "Service");
        return service;
    }

    @Transactional
    public <R> IdempotencyService.Result<R> create(CreateServiceCommand command, AuthenticatedUser actor,
                                                   String idempotencyKey, Class<R> responseType,
                                                   Function<ServiceDefinition, R> mapper, Function<R, Object> idOf) {
        return idempotencyService.execute(actor.id(), "SERVICE_CREATE", idempotencyKey, command, responseType, idOf,
                () -> mapper.apply(doCreate(command, actor)));
    }

    private ServiceDefinition doCreate(CreateServiceCommand command, AuthenticatedUser actor) {
        actor.requireDepartmentScope(command.departmentId(), "Department");
        departmentService.requireActive(command.departmentId());
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        if (serviceRepository.existsByCode(code)) {
            throw ApiException.duplicate("A service with code " + code + " already exists.");
        }
        Map<String, Object> schema = command.formSchema() == null ? new LinkedHashMap<>() : command.formSchema();
        formSchemaValidator.validate(schema);
        ServiceDefinition service = serviceRepository.saveAndFlush(new ServiceDefinition(command.departmentId(), code,
                command.name().trim(), command.description().trim(), normalizeCategory(command.category()),
                blankToNull(command.eligibilityText()), command.processingDays(), fee(command.feeAmount()), schema));
        auditService.record(AuditEvent.success(AuditActions.SERVICE_CREATED, "SERVICE", service.getId())
                .detail("code", code)
                .detail("departmentId", command.departmentId().toString()));
        return service;
    }

    @Transactional
    public ServiceDefinition update(UUID id, UpdateServiceCommand command, AuthenticatedUser actor) {
        ServiceDefinition service = get(id, actor);
        if (!service.isVersion(command.version())) {
            throw ApiException.staleVersion();
        }
        if (command.formSchema() != null) {
            formSchemaValidator.validate(command.formSchema());
        }
        service.updateDetails(trimOrNull(command.name()), trimOrNull(command.description()),
                command.category() == null ? null : normalizeCategory(command.category()), command.eligibilityText(),
                command.processingDays(), command.feeAmount() == null ? null : fee(command.feeAmount()),
                command.formSchema());
        serviceRepository.saveAndFlush(service);
        auditService.record(AuditEvent.success(AuditActions.SERVICE_UPDATED, "SERVICE", id));
        return service;
    }

    @Transactional
    public ServiceRequirement addRequirement(UUID serviceId, RequirementCommand command, AuthenticatedUser actor) {
        ServiceDefinition service = get(serviceId, actor);
        ServiceRequirement requirement = service.addRequirement(new ServiceRequirement(
                command.code().trim().toUpperCase(Locale.ROOT), command.name().trim(), blankToNull(command.description()),
                command.type(), command.mandatory() == null || command.mandatory(),
                blankToNull(command.allowedMediaTypes()), command.maxSizeBytes(),
                command.displayOrder() == null ? service.getRequirements().size() : command.displayOrder()));
        // flush (not save/merge): cascade-persists this exact instance so the returned requirement has its id
        serviceRepository.flush();
        auditService.record(AuditEvent.success(AuditActions.SERVICE_REQUIREMENTS_CHANGED, "SERVICE", serviceId)
                .detail("added", requirement.getCode()));
        return requirement;
    }

    @Transactional
    public ServiceRequirement updateRequirement(UUID serviceId, UUID requirementId, RequirementCommand command,
                                                AuthenticatedUser actor) {
        ServiceDefinition service = get(serviceId, actor);
        service.ensureEditable();
        ServiceRequirement requirement = service.findRequirement(requirementId)
                .orElseThrow(() -> ApiException.notFound("Requirement"));
        requirement.update(trimOrNull(command.name()), command.description(), command.mandatory(),
                command.allowedMediaTypes(), command.maxSizeBytes(), command.displayOrder());
        service.markChanged();
        serviceRepository.saveAndFlush(service);
        auditService.record(AuditEvent.success(AuditActions.SERVICE_REQUIREMENTS_CHANGED, "SERVICE", serviceId)
                .detail("updated", requirement.getCode()));
        return requirement;
    }

    @Transactional
    public void removeRequirement(UUID serviceId, UUID requirementId, AuthenticatedUser actor) {
        ServiceDefinition service = get(serviceId, actor);
        ServiceRequirement requirement = service.findRequirement(requirementId)
                .orElseThrow(() -> ApiException.notFound("Requirement"));
        service.removeRequirement(requirement);
        serviceRepository.saveAndFlush(service);
        auditService.record(AuditEvent.success(AuditActions.SERVICE_REQUIREMENTS_CHANGED, "SERVICE", serviceId)
                .detail("removed", requirement.getCode()));
    }

    /** Publishes the working copy as a new immutable version. */
    @Transactional
    public ServiceVersion publish(UUID id, Long expectedVersion, AuthenticatedUser actor) {
        ServiceDefinition service = get(id, actor);
        if (expectedVersion != null && !service.isVersion(expectedVersion)) {
            throw ApiException.staleVersion();
        }
        departmentService.requireActive(service.getDepartmentId());
        formSchemaValidator.validate(service.getFormSchema());
        int versionNumber = service.publish();
        serviceRepository.saveAndFlush(service);
        ServiceVersion version = versionRepository.saveAndFlush(
                new ServiceVersion(service, versionNumber, clock.instant(), actor.id()));
        auditService.record(AuditEvent.success(AuditActions.SERVICE_PUBLISHED, "SERVICE", id)
                .detail("version", versionNumber));
        return version;
    }

    @Transactional
    public ServiceDefinition deprecate(UUID id, AuthenticatedUser actor) {
        ServiceDefinition service = get(id, actor);
        service.deprecate();
        serviceRepository.saveAndFlush(service);
        auditService.record(AuditEvent.success(AuditActions.SERVICE_DEPRECATED, "SERVICE", id));
        return service;
    }

    private static Specification<ServiceDefinition> specification(ManageFilter filter, AuthenticatedUser actor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.hasGlobalScope()) {
                if (actor.departmentIds().isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("departmentId").in(actor.departmentIds()));
            }
            if (filter.departmentId() != null) {
                predicates.add(cb.equal(root.get("departmentId"), filter.departmentId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.category() != null && !filter.category().isBlank()) {
                predicates.add(cb.equal(root.get("category"), normalizeCategory(filter.category())));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), ServiceCatalogueQueryService.likePattern(filter.q()), '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String normalizeCategory(String category) {
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal fee(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }
}
