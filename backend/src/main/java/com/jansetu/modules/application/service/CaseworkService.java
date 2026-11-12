package com.jansetu.modules.application.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.modules.application.domain.Application;
import com.jansetu.modules.application.domain.ApplicationStatus;
import com.jansetu.modules.application.domain.CaseRecords.Comment;
import com.jansetu.modules.application.domain.CaseRecords.Decision;
import com.jansetu.modules.application.domain.ClarificationRequest;
import com.jansetu.modules.application.domain.WorkflowInstance;
import com.jansetu.modules.application.domain.WorkflowTask;
import com.jansetu.modules.application.repository.ClarificationRepository;
import com.jansetu.modules.application.repository.CommentRepository;
import com.jansetu.modules.application.repository.DecisionRepository;
import com.jansetu.modules.application.repository.WorkflowInstanceRepository;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService.ServiceSnapshot;
import com.jansetu.modules.document.domain.DocumentStatus;
import com.jansetu.modules.document.service.DocumentService;
import com.jansetu.modules.document.service.DocumentService.DocumentView;
import com.jansetu.modules.identity.service.UserDirectoryService;
import com.jansetu.modules.identity.service.UserDirectoryService.UserSummary;
import com.jansetu.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff-side case handling. Every command re-checks permission, department scope, task assignment and the current
 * status; optimistic locking on applications and tasks prevents two simultaneous decisions.
 */
@Service
public class CaseworkService {

    public record TaskQueueItem(UUID taskId, UUID applicationId, String referenceNumber, String applicationStatus,
                                String serviceName, String step, String taskStatus, UUID assigneeId,
                                String assigneeName, Instant dueAt, boolean overdue, boolean dueSoon,
                                Instant createdAt, long version) {
    }

    public record TaskFilter(boolean mine, boolean unassigned, String step, boolean overdue, UUID departmentId) {
    }

    private final CaseSupport support;
    private final ClarificationRepository clarifications;
    private final DecisionRepository decisions;
    private final CommentRepository comments;
    private final WorkflowInstanceRepository instances;
    private final DocumentService documents;
    private final ServiceCatalogueQueryService catalogue;
    private final UserDirectoryService users;

    public CaseworkService(CaseSupport support, ClarificationRepository clarifications, DecisionRepository decisions,
                           CommentRepository comments, WorkflowInstanceRepository instances, DocumentService documents,
                           ServiceCatalogueQueryService catalogue, UserDirectoryService users) {
        this.support = support;
        this.clarifications = clarifications;
        this.decisions = decisions;
        this.comments = comments;
        this.instances = instances;
        this.documents = documents;
        this.catalogue = catalogue;
        this.users = users;
    }

    // ---- Review stage ---------------------------------------------------------------------------------------

    /** Claims (or starts the already assigned) review task: SUBMITTED/RESUBMITTED → UNDER_REVIEW. */
    @Transactional
    public void startReview(UUID id, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "TASK:CLAIM");
        if (app.getStatus() != ApplicationStatus.SUBMITTED && app.getStatus() != ApplicationStatus.RESUBMITTED) {
            throw ApiException.invalidState("Review can only start on a submitted or resubmitted application.");
        }
        actor.requirePermission("APPLICATION:RECOMMEND");
        WorkflowTask task = support.requireActiveTask(id, WorkflowTask.REVIEW);
        task.start(actor.id(), support.now());
        support.tasks.saveAndFlush(task);
        support.transition(app, ApplicationStatus.UNDER_REVIEW, "start_review", actor.id(), null, true);
    }

    @Transactional
    public void requestClarification(UUID id, String question, Integer dueInDays, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "APPLICATION:CLARIFY");
        WorkflowTask task = requireMyReview(app, actor);
        Instant now = support.now();
        int days = dueInDays == null ? 7 : dueInDays;
        if (days < 1 || days > 60) {
            throw ApiException.businessRule("The response deadline must be between 1 and 60 days.");
        }
        String text = CaseSupport.requireText(question, "A clarification question");
        clarifications.save(new ClarificationRequest(id, actor.id(), text, now.plus(Duration.ofDays(days)), now));
        task.block();
        support.transition(app, ApplicationStatus.CLARIFICATION_REQUIRED, "request_clarification", actor.id(), text, true);
        support.notifyCitizen(app, "CLARIFICATION_REQUESTED", "Clarification requested",
                "The department needs more information on " + app.getReferenceNumber() + ". Please respond within "
                        + days + " days.");
    }

    @Transactional
    public void recommend(UUID id, String remarks, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "APPLICATION:RECOMMEND");
        WorkflowTask review = requireMyReview(app, actor);
        requireDocumentsVerified(app);
        Instant now = support.now();
        review.complete("RECOMMENDED", now);
        decisions.save(new Decision(id, "RECOMMENDED", blankToNull(remarks), actor.id(), now));
        support.tasks.saveAndFlush(review);
        WorkflowInstance instance = activeInstance(id);
        instance.moveTo(WorkflowTask.APPROVAL);
        support.tasks.saveAndFlush(new WorkflowTask(instance.getId(), id, app.getDepartmentId(), WorkflowTask.APPROVAL,
                review.getDueAt(), now));
        support.transition(app, ApplicationStatus.PENDING_APPROVAL, "recommend", actor.id(), blankToNull(remarks), false);
    }

    // ---- Decisions ------------------------------------------------------------------------------------------

    @Transactional
    public void approve(UUID id, String remarks, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "APPLICATION:APPROVE");
        WorkflowTask approval = requireApprover(app, actor);
        Instant now = support.now();
        approval.start(actor.id(), now);
        approval.complete("APPROVED", now);
        decisions.save(new Decision(id, "APPROVED", blankToNull(remarks), actor.id(), now));
        support.tasks.saveAndFlush(approval);
        support.transition(app, ApplicationStatus.APPROVED, "approve", actor.id(), blankToNull(remarks), true);
        support.notifyCitizen(app, "APPLICATION_APPROVED", "Application approved",
                "Your application " + app.getReferenceNumber() + " has been approved.");
    }

    /** Rejects at review (assigned officer) or approval (reviewer). A reason is mandatory. */
    @Transactional
    public void reject(UUID id, String reason, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "APPLICATION:REJECT");
        String text = CaseSupport.requireText(reason, "A rejection reason");
        WorkflowTask task;
        if (app.getStatus() == ApplicationStatus.UNDER_REVIEW) {
            task = requireMyReview(app, actor);
        } else if (app.getStatus() == ApplicationStatus.PENDING_APPROVAL) {
            actor.requirePermission("APPLICATION:APPROVE");
            task = requireApprover(app, actor);
            task.start(actor.id(), support.now());
        } else {
            throw ApiException.invalidState("An application in status " + app.getStatus() + " cannot be rejected.");
        }
        Instant now = support.now();
        task.complete("REJECTED", now);
        support.tasks.saveAndFlush(task);
        decisions.save(new Decision(id, "REJECTED", text, actor.id(), now));
        activeInstance(id).finish("COMPLETED", now);
        support.transition(app, ApplicationStatus.REJECTED, "reject", actor.id(), text, true);
        support.notifyCitizen(app, "APPLICATION_REJECTED", "Application rejected",
                "Your application " + app.getReferenceNumber() + " was rejected. See the decision for the reason.");
    }

    /** Reviewer returns the case to the recommending officer for correction. */
    @Transactional
    public void returnForCorrection(UUID id, String reason, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "APPLICATION:APPROVE");
        String text = CaseSupport.requireText(reason, "A reason for returning the case");
        WorkflowTask approval = requireApprover(app, actor);
        Instant now = support.now();
        approval.start(actor.id(), now);
        approval.complete("RETURNED", now);
        support.tasks.saveAndFlush(approval);
        decisions.save(new Decision(id, "RETURNED", text, actor.id(), now));
        WorkflowInstance instance = activeInstance(id);
        instance.moveTo(WorkflowTask.REVIEW);
        WorkflowTask review = new WorkflowTask(instance.getId(), id, app.getDepartmentId(), WorkflowTask.REVIEW,
                approval.getDueAt(), now);
        UUID recommender = lastRecommender(id);
        if (recommender != null) {
            review.start(recommender, now);
        }
        support.tasks.saveAndFlush(review);
        support.transition(app, ApplicationStatus.UNDER_REVIEW, "return_for_correction", actor.id(), text, false);
        support.notifyStaff(recommender, app, "CASE_RETURNED", "Case returned for correction",
                app.getReferenceNumber() + " was returned by the reviewer.");
    }

    @Transactional
    public void complete(UUID id, String remarks, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "APPLICATION:COMPLETE");
        Instant now = support.now();
        activeInstance(id).finish("COMPLETED", now);
        support.transition(app, ApplicationStatus.COMPLETED, "complete", actor.id(), blankToNull(remarks), true);
        support.notifyCitizen(app, "APPLICATION_COMPLETED", "Application completed",
                "Your application " + app.getReferenceNumber() + " has been completed.");
    }

    // ---- Documents & comments -------------------------------------------------------------------------------

    @Transactional
    public DocumentView verifyDocument(UUID id, UUID documentId, boolean accept, String reason, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, "DOCUMENT:VERIFY");
        if (app.getStatus() == ApplicationStatus.UNDER_REVIEW) {
            requireMyReview(app, actor);
        } else if (app.getStatus() != ApplicationStatus.PENDING_APPROVAL) {
            throw ApiException.invalidState("Documents can only be verified while the application is under review or approval.");
        }
        DocumentView doc = documents.get(documentId);
        if (!id.equals(doc.applicationId())) {
            throw ApiException.notFound("Document");
        }
        return accept ? documents.verify(documentId, actor.id())
                : documents.reject(documentId, actor.id(), CaseSupport.requireText(reason, "A rejection reason"));
    }

    @Transactional
    public void addComment(UUID id, String body, AuthenticatedUser actor) {
        Application app = support.loadForStaff(id, actor, CaseSupport.READ_DEPARTMENT);
        comments.save(new Comment(app.getId(), actor.id(), CaseSupport.requireText(body, "A comment"), support.now()));
        support.audit.record(AuditEvent.success("APPLICATION_COMMENT_ADDED", "APPLICATION", id));
    }

    // ---- Tasks ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<TaskQueueItem> tasks(TaskFilter filter, PageQuery pageQuery, AuthenticatedUser actor) {
        actor.requirePermission("TASK:READ");
        if (filter.departmentId() != null) {
            actor.requireDepartmentScope(filter.departmentId(), "Department");
        }
        Instant now = support.now();
        Page<WorkflowTask> page = support.tasks.findAll(taskSpec(filter, actor, now),
                pageQuery.toPageable(Map.of("dueAt", "dueAt", "createdAt", "createdAt"), Sort.by("dueAt")));
        List<UUID> appIds = page.getContent().stream().map(WorkflowTask::getApplicationId).toList();
        Map<UUID, Application> apps = support.applications.findAllById(appIds).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        Map<UUID, String> serviceNames = catalogue.serviceNames(apps.values().stream().map(Application::getServiceId).distinct().toList());
        Set<UUID> people = new HashSet<>();
        page.getContent().forEach(t -> people.add(t.getAssigneeId()));
        people.remove(null);
        Map<UUID, UserSummary> names = users.summaries(people);
        return page.map(t -> {
            Application a = apps.get(t.getApplicationId());
            boolean overdue = t.getDueAt() != null && t.getDueAt().isBefore(now) && !"BLOCKED".equals(t.getStatus());
            boolean dueSoon = !overdue && t.getDueAt() != null && t.getDueAt().isBefore(now.plus(Duration.ofDays(2)));
            UserSummary assignee = t.getAssigneeId() == null ? null : names.get(t.getAssigneeId());
            return new TaskQueueItem(t.getId(), t.getApplicationId(), a.getReferenceNumber(), a.getStatus().name(),
                    serviceNames.get(a.getServiceId()), t.getStepCode(), t.getStatus(), t.getAssigneeId(),
                    assignee == null ? null : assignee.fullName(), t.getDueAt(), overdue, dueSoon, t.getCreatedAt(),
                    t.getVersion());
        });
    }

    /** Department admins assign or reassign an active task to an eligible member of the department. */
    @Transactional
    public void assignTask(UUID taskId, UUID assigneeId, AuthenticatedUser actor) {
        actor.requirePermission("TASK:ASSIGN");
        WorkflowTask task = support.tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task"));
        actor.requireDepartmentScope(task.getDepartmentId(), "Task");
        String required = WorkflowTask.REVIEW.equals(task.getStepCode()) ? "APPLICATION:RECOMMEND" : "APPLICATION:APPROVE";
        users.requireEligibleAssignee(assigneeId, task.getDepartmentId(), required);
        if (WorkflowTask.APPROVAL.equals(task.getStepCode()) && assigneeId.equals(lastRecommender(task.getApplicationId()))) {
            throw ApiException.businessRule("The recommending officer cannot approve the same case (separation of duties).");
        }
        task.assign(assigneeId, support.now());
        support.tasks.saveAndFlush(task);
        Application app = support.load(task.getApplicationId());
        support.audit.record(AuditEvent.success("TASK_ASSIGNED", "TASK", taskId).detail("assigneeId", assigneeId.toString()));
        support.notifyStaff(assigneeId, app, "TASK_ASSIGNED", "New task assigned",
                app.getReferenceNumber() + " (" + task.getStepCode().toLowerCase() + ") was assigned to you.");
    }

    /** Claims a task: review tasks start the review; approval tasks are reserved for the claiming reviewer. */
    @Transactional
    public void claimTask(UUID taskId, AuthenticatedUser actor) {
        WorkflowTask task = support.tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task"));
        actor.requireDepartmentScope(task.getDepartmentId(), "Task");
        if (WorkflowTask.REVIEW.equals(task.getStepCode())) {
            startReview(task.getApplicationId(), actor);
            return;
        }
        actor.requirePermission("APPLICATION:APPROVE");
        if (actor.id().equals(lastRecommender(task.getApplicationId()))) {
            throw ApiException.businessRule("The recommending officer cannot approve the same case (separation of duties).");
        }
        task.start(actor.id(), support.now());
        support.tasks.saveAndFlush(task);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private WorkflowTask requireMyReview(Application app, AuthenticatedUser actor) {
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw ApiException.invalidState("The application is not under review.");
        }
        WorkflowTask task = support.requireActiveTask(app.getId(), WorkflowTask.REVIEW);
        if (!task.isAssignedTo(actor.id())) {
            throw ApiException.accessDenied("Only the officer assigned to this review can perform this action.");
        }
        return task;
    }

    private WorkflowTask requireApprover(Application app, AuthenticatedUser actor) {
        if (app.getStatus() != ApplicationStatus.PENDING_APPROVAL) {
            throw ApiException.invalidState("The application is not pending approval.");
        }
        WorkflowTask task = support.requireActiveTask(app.getId(), WorkflowTask.APPROVAL);
        if (task.getAssigneeId() != null && !task.isAssignedTo(actor.id())) {
            throw ApiException.accessDenied("This approval is assigned to another reviewer.");
        }
        if (actor.id().equals(lastRecommender(app.getId()))) {
            throw ApiException.businessRule("The recommending officer cannot approve the same case (separation of duties).");
        }
        return task;
    }

    private void requireDocumentsVerified(Application app) {
        ServiceSnapshot service = catalogue.snapshotOfVersion(app.getServiceVersionId());
        Map<String, DocumentStatus> statuses = documents.requirementStatuses(app.getId());
        for (Map<String, Object> r : service.requirements()) {
            if ("DOCUMENT".equals(r.get("requirementType")) && Boolean.TRUE.equals(r.get("mandatory"))
                    && statuses.get((String) r.get("code")) != DocumentStatus.VERIFIED) {
                throw ApiException.businessRule("Verify all mandatory documents before recommending. Pending: " + r.get("name") + ".");
            }
        }
    }

    private UUID lastRecommender(UUID applicationId) {
        return decisions.findFirstByApplicationIdAndDecisionOrderByDecidedAtDesc(applicationId, "RECOMMENDED")
                .map(Decision::getDecidedBy).orElse(null);
    }

    private WorkflowInstance activeInstance(UUID applicationId) {
        return instances.findByApplicationIdAndStatus(applicationId, "ACTIVE")
                .orElseThrow(() -> ApiException.invalidState("The application has no active workflow."));
    }

    private static Specification<WorkflowTask> taskSpec(TaskFilter f, AuthenticatedUser actor, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(root.get("status").in(CaseSupport.ACTIVE_TASK));
            if (!actor.hasGlobalScope()) {
                if (actor.departmentIds().isEmpty()) {
                    return cb.disjunction();
                }
                p.add(root.get("departmentId").in(actor.departmentIds()));
            }
            if (f.departmentId() != null) p.add(cb.equal(root.get("departmentId"), f.departmentId()));
            if (f.mine()) p.add(cb.equal(root.get("assigneeId"), actor.id()));
            if (f.unassigned()) p.add(cb.isNull(root.get("assigneeId")));
            if (f.step() != null && !f.step().isBlank()) p.add(cb.equal(root.get("stepCode"), f.step()));
            if (f.overdue()) {
                p.add(cb.lessThan(root.get("dueAt"), now));
                p.add(cb.notEqual(root.get("status"), "BLOCKED"));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
