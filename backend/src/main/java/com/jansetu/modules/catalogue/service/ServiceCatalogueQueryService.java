package com.jansetu.modules.catalogue.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.modules.catalogue.domain.ServiceDefinition;
import com.jansetu.modules.catalogue.domain.ServiceStatus;
import com.jansetu.modules.catalogue.domain.ServiceVersion;
import com.jansetu.modules.catalogue.repository.ServiceDefinitionRepository;
import com.jansetu.modules.catalogue.repository.ServiceVersionRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public (anonymous) catalogue reads. Only the latest published version of PUBLISHED services is visible. */
@Service
public class ServiceCatalogueQueryService {

    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name", "category", "category", "processingDays", "processingDays", "publishedAt", "publishedAt");

    public record PublicFilter(UUID departmentId, String category, String q) {
    }

    /** A published version together with stable service identity. */
    public record PublishedService(UUID serviceId, String code, UUID departmentId, ServiceVersion version) {
    }

    private final ServiceVersionRepository versionRepository;
    private final ServiceDefinitionRepository serviceRepository;

    public ServiceCatalogueQueryService(ServiceVersionRepository versionRepository,
                                        ServiceDefinitionRepository serviceRepository) {
        this.versionRepository = versionRepository;
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public Page<PublishedService> listPublished(PublicFilter filter, PageQuery pageQuery) {
        Page<ServiceVersion> page = versionRepository.findAll(publishedSpecification(filter),
                pageQuery.toPageable(SORTABLE, Sort.by("name")));
        Map<UUID, ServiceDefinition> services = new java.util.HashMap<>();
        serviceRepository.findAllById(page.getContent().stream().map(ServiceVersion::getServiceId).toList())
                .forEach(s -> services.put(s.getId(), s));
        return page.map(v -> {
            ServiceDefinition s = services.get(v.getServiceId());
            return new PublishedService(s.getId(), s.getCode(), s.getDepartmentId(), v);
        });
    }

    @Transactional(readOnly = true)
    public PublishedService getPublished(UUID serviceId) {
        ServiceDefinition service = serviceRepository.findById(serviceId)
                .filter(s -> s.getStatus() == ServiceStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Service"));
        ServiceVersion version = versionRepository.findByServiceIdAndVersionNumber(serviceId, service.getCurrentVersion())
                .orElseThrow(() -> ApiException.notFound("Service"));
        return new PublishedService(service.getId(), service.getCode(), service.getDepartmentId(), version);
    }

    /** Any published version (including those of later-deprecated services) for historical reference. */
    @Transactional(readOnly = true)
    public PublishedService getVersion(UUID serviceId, int versionNumber) {
        ServiceDefinition service = serviceRepository.findById(serviceId)
                .filter(s -> s.getStatus() != ServiceStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Service version"));
        ServiceVersion version = versionRepository.findByServiceIdAndVersionNumber(serviceId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Service version"));
        return new PublishedService(service.getId(), service.getCode(), service.getDepartmentId(), version);
    }

    private static Specification<ServiceVersion> publishedSpecification(PublicFilter filter) {
        return (root, query, cb) -> {
            Subquery<UUID> latest = query.subquery(UUID.class);
            Root<ServiceDefinition> service = latest.from(ServiceDefinition.class);
            List<Predicate> servicePredicates = new ArrayList<>();
            servicePredicates.add(cb.equal(service.get("id"), root.get("serviceId")));
            servicePredicates.add(cb.equal(service.get("status"), ServiceStatus.PUBLISHED));
            servicePredicates.add(cb.equal(service.get("currentVersion"), root.get("versionNumber")));
            if (filter.departmentId() != null) {
                servicePredicates.add(cb.equal(service.get("departmentId"), filter.departmentId()));
            }
            latest.select(service.get("id")).where(servicePredicates.toArray(Predicate[]::new));

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.exists(latest));
            if (filter.category() != null && !filter.category().isBlank()) {
                predicates.add(cb.equal(root.get("category"), ServiceCatalogueService.normalizeCategory(filter.category())));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), likePattern(filter.q()), '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String likePattern(String q) {
        return "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
