package com.jansetu.modules.application.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.idempotency.IdempotencyService;
import com.jansetu.modules.application.domain.Application;
import com.jansetu.modules.application.domain.ApplicationStatus;
import com.jansetu.modules.application.domain.ClarificationRequest;
import com.jansetu.modules.application.domain.WorkflowInstance;
import com.jansetu.modules.application.domain.WorkflowTask;
import com.jansetu.modules.application.repository.ClarificationRepository;
import com.jansetu.modules.application.repository.WorkflowInstanceRepository;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService.ServiceSnapshot;
import com.jansetu.modules.document.domain.DocumentStatus;
import com.jansetu.modules.document.service.DocumentService;
import com.jansetu.modules.document.service.DocumentService.DocumentView;
import com.jansetu.security.AuthenticatedUser;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Citizen-side application use cases. */
@Service
public class ApplicationService {

    public record SubmitResult(UUID id, String referenceNumber, String status) {
    }

    private final CaseSupport support;
    private final ServiceCatalogueQueryService catalogue;
    private final FormDataValidator formValidator;
    private final DocumentService documents;
    private final WorkflowInstanceRepository instances;
    private final ClarificationRepository clarifications;
    private final IdempotencyService idempotency;

    public ApplicationService(CaseSupport support, ServiceCatalogueQueryService catalogue, FormDataValidator formValidator,
                              DocumentService documents, WorkflowInstanceRepository instances,
                              ClarificationRepository clarifications, IdempotencyService idempotency) {
        this.support = support;
        this.catalogue = catalogue;
        this.formValidator = formValidator;
        this.documents = documents;
        this.instances = instances;
        this.clarifications = clarifications;
        this.idempotency = idempotency;
    }

    @Transactional
    public UUID createDraft(UUID serviceId, AuthenticatedUser actor) {
        actor.requirePermission("APPLICATION:CREATE");
        ServiceSnapshot service = catalogue.latestPublishedSnapshot(serviceId);
        Application app = support.applications.saveAndFlush(
                new Application(actor.id(), service.serviceId(), service.serviceVersionId(), service.departmentId()));
        support.history.save(new com.jansetu.modules.application.domain.CaseRecords.StatusHistory(app.getId(), null,
                ApplicationStatus.DRAFT, "create", actor.id(), null, true, support.now()));
        support.audit.record(com.jansetu.modules.audit.service.AuditEvent
                .success("APPLICATION_CREATE", "APPLICATION", app.getId()).detail("serviceId", serviceId.toString()));
        return app.getId();
    }

    @Transactional
    public void updateDraft(UUID id, Map<String, Object> formData, Long version, AuthenticatedUser actor) {
        Application app = support.loadOwned(id, actor);
        if (!app.isVersion(version)) {
            throw ApiException.staleVersion();
        }
        ServiceSnapshot service = catalogue.snapshotOfVersion(app.getServiceVersionId());
        formValidator.validate(service.formSchema(), service.requirements(), formData, false);
        app.replaceFormData(formData);
        support.applications.saveAndFlush(app);
    }

    @Transactional
    public DocumentView uploadDocument(UUID id, String requirementCode, DocumentService.UploadCommand file,
                                       AuthenticatedUser actor) {
        actor.requirePermission("DOCUMENT:UPLOAD");
        Application app = support.loadOwned(id, actor);
        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.CLARIFICATION_REQUIRED) {
            throw ApiException.invalidState("Documents can only be uploaded to a draft or during clarification.");
        }
        ServiceSnapshot service = catalogue.snapshotOfVersion(app.getServiceVersionId());
        Map<String, Object> requirement = service.requirements().stream()
                .filter(r -> requirementCode.equals(r.get("code")) && "DOCUMENT".equals(r.get("requirementType")))
                .findFirst()
                .orElseThrow(() -> ApiException.businessRule("This service has no document requirement " + requirementCode + "."));
        Long maxBytes = requirement.get("maxSizeBytes") instanceof Number n ? n.longValue() : null;
        return documents.uploadForApplication(id, app.getCitizenId(), requirementCode,
                (String) requirement.get("allowedMediaTypes"), maxBytes, file, actor.id());
    }

    @Transactional
    public SubmitResult submit(UUID id, String idempotencyKey, AuthenticatedUser actor) {
        actor.requirePermission("APPLICATION:SUBMIT");
        return idempotency.execute(actor.id(), "APPLICATION_SUBMIT", idempotencyKey, Map.of("applicationId", id),
                SubmitResult.class, SubmitResult::id, () -> doSubmit(id, actor)).value();
    }

    private SubmitResult doSubmit(UUID id, AuthenticatedUser actor) {
        Application app = support.loadOwned(id, actor);
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw ApiException.invalidState("Only a draft application can be submitted.");
        }
        ServiceSnapshot service = catalogue.snapshotOfVersion(app.getServiceVersionId());
        formValidator.validate(service.formSchema(), service.requirements(), app.getFormData(), true);
        Map<String, DocumentStatus> uploaded = documents.requirementStatuses(id);
        for (Map<String, Object> r : service.requirements()) {
            if ("DOCUMENT".equals(r.get("requirementType")) && Boolean.TRUE.equals(r.get("mandatory"))
                    && !uploaded.containsKey((String) r.get("code"))) {
                throw ApiException.businessRule("Required document missing: " + r.get("name") + ".");
            }
        }
        Instant now = support.now();
        app.assignReference(String.format("JAN-%d-%06d", Year.now(ZoneOffset.UTC).getValue(),
                support.applications.nextReferenceValue()));
        support.transition(app, ApplicationStatus.SUBMITTED, "submit", actor.id(), null, true);
        WorkflowInstance instance = instances.saveAndFlush(new WorkflowInstance(app.getId(), now));
        support.tasks.saveAndFlush(new WorkflowTask(instance.getId(), app.getId(), app.getDepartmentId(),
                WorkflowTask.REVIEW, now.plus(Duration.ofDays(service.processingDays())), now));
        support.notifyCitizen(app, "APPLICATION_SUBMITTED", "Application submitted",
                "Your application " + app.getReferenceNumber() + " for " + service.name() + " was submitted.");
        return new SubmitResult(app.getId(), app.getReferenceNumber(), app.getStatus().name());
    }

    @Transactional
    public void withdraw(UUID id, String reason, AuthenticatedUser actor) {
        Application app = support.loadOwned(id, actor);
        support.transition(app, ApplicationStatus.WITHDRAWN, "withdraw", actor.id(), blankToNull(reason), true);
        support.activeTask(id).ifPresent(t -> t.cancel(support.now()));
        instances.findByApplicationIdAndStatus(id, "ACTIVE").ifPresent(i -> i.finish("CANCELLED", support.now()));
    }

    /** Citizen answers an open clarification request; the case returns to the assigned officer. */
    @Transactional
    public void respondToClarification(UUID id, String response, AuthenticatedUser actor) {
        Application app = support.loadOwned(id, actor);
        if (app.getStatus() != ApplicationStatus.CLARIFICATION_REQUIRED) {
            throw ApiException.invalidState("No clarification is pending for this application.");
        }
        ClarificationRequest request = clarifications.findFirstByApplicationIdAndStatus(id, "OPEN")
                .orElseThrow(() -> ApiException.invalidState("No clarification is pending for this application."));
        Instant now = support.now();
        request.respond(CaseSupport.requireText(response, "A response"), now);
        if (documents.requirementStatuses(id).containsValue(DocumentStatus.REJECTED)) {
            throw ApiException.businessRule("Replace the rejected document(s) before responding.");
        }
        WorkflowTask task = support.requireActiveTask(id, WorkflowTask.REVIEW);
        task.resume(Duration.between(request.getCreatedAt(), now));
        support.transition(app, ApplicationStatus.RESUBMITTED, "respond_clarification", actor.id(), null, true);
        support.notifyStaff(task.getAssigneeId(), app, "CLARIFICATION_RESPONDED", "Clarification received",
                "The applicant responded on " + app.getReferenceNumber() + ".");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
