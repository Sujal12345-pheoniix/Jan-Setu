package com.jansetu.modules.application.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.modules.application.domain.Application;
import com.jansetu.modules.application.domain.ApplicationStatus;
import com.jansetu.modules.application.domain.CaseRecords.Decision;
import com.jansetu.modules.application.domain.CaseRecords.StatusHistory;
import com.jansetu.modules.application.domain.WorkflowTask;
import com.jansetu.modules.application.repository.ClarificationRepository;
import com.jansetu.modules.application.repository.CommentRepository;
import com.jansetu.modules.application.repository.DecisionRepository;
import com.jansetu.modules.application.service.ApplicationViews.ClarificationItem;
import com.jansetu.modules.application.service.ApplicationViews.CommentItem;
import com.jansetu.modules.application.service.ApplicationViews.DecisionItem;
import com.jansetu.modules.application.service.ApplicationViews.Detail;
import com.jansetu.modules.application.service.ApplicationViews.HistoryItem;
import com.jansetu.modules.application.service.ApplicationViews.ServiceInfo;
import com.jansetu.modules.application.service.ApplicationViews.Summary;
import com.jansetu.modules.application.service.ApplicationViews.TaskItem;
import com.jansetu.modules.catalogue.service.ServiceCatalogueQueryService;
import com.jansetu.modules.document.service.DocumentService;
import com.jansetu.modules.identity.service.UserDirectoryService;
import com.jansetu.modules.identity.service.UserDirectoryService.UserSummary;
import com.jansetu.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

@Service
public class ApplicationQueryService {

    private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "submittedAt", "submittedAt",
            "updatedAt", "updatedAt", "status", "status", "referenceNumber", "referenceNumber");

    public record ApplicationFilter(ApplicationStatus status, UUID serviceId, UUID departmentId, String q,
                                    boolean assignedToMe, boolean overdue) {
    }

    private final CaseSupport support;
    private final ClarificationRepository clarifications;
    private final DecisionRepository decisions;
    private final CommentRepository comments;
    private final DocumentService documents;
    private final ServiceCatalogueQueryService catalogue;
    private final UserDirectoryService users;

    public ApplicationQueryService(CaseSupport support, ClarificationRepository clarifications,
                                   DecisionRepository decisions, CommentRepository comments, DocumentService documents,
                                   ServiceCatalogueQueryService catalogue, UserDirectoryService users) {
        this.support = support;
        this.clarifications = clarifications;
        this.decisions = decisions;
        this.comments = comments;
        this.documents = documents;
        this.catalogue = catalogue;
        this.users = users;
    }

    /** Citizens see their own applications; staff with APPLICATION:READ_DEPARTMENT see their departments' cases. */
    @Transactional(readOnly = true)
    public Page<Summary> list(ApplicationFilter filter, PageQuery pageQuery, AuthenticatedUser actor) {
        boolean staff = actor.hasPermission(CaseSupport.READ_DEPARTMENT);
        if (!staff && !actor.hasPermission("APPLICATION:READ_OWN")) {
            throw ApiException.accessDenied("You do not have permission to view applications.");
        }
        if (staff && filter.departmentId() != null) {
            actor.requireDepartmentScope(filter.departmentId(), "Department");
        }
        Page<Application> page = support.applications.findAll(specification(filter, actor, staff, support.now()),
                pageQuery.toPageable(SORTABLE, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return toSummaries(page, staff);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id, AuthenticatedUser actor) {
        Application app = support.loadReadable(id, actor);
        boolean staff = support.isStaffViewer(app, actor) && !app.isOwnedBy(actor.id());
        List<WorkflowTask> allTasks = support.tasks.findByApplicationIdOrderByCreatedAtAsc(id);
        List<Decision> decisionList = decisions.findByApplicationIdOrderByDecidedAtAsc(id);
        List<StatusHistory> historyList = staff
                ? support.history.findByApplicationIdOrderByOccurredAtAsc(id)
                : support.history.findByApplicationIdAndCitizenVisibleTrueOrderByOccurredAtAsc(id);
        var commentList = staff ? comments.findByApplicationIdOrderByCreatedAtAsc(id) : List.<com.jansetu.modules.application.domain.CaseRecords.Comment>of();

        Set<UUID> people = new HashSet<>();
        people.add(app.getCitizenId());
        allTasks.forEach(t -> people.add(t.getAssigneeId()));
        decisionList.forEach(d -> people.add(d.getDecidedBy()));
        historyList.forEach(h -> people.add(h.getActorId()));
        commentList.forEach(c -> people.add(c.getAuthorId()));
        people.remove(null);
        Map<UUID, UserSummary> names = users.summaries(people);

        var snapshot = catalogue.snapshotOfVersion(app.getServiceVersionId());
        Summary summary = summary(app, snapshot.name(), allTasks.stream().filter(WorkflowTask::isActive).reduce((a, b) -> b).orElse(null),
                names, staff);
        return new Detail(
                summary,
                new ServiceInfo(snapshot.serviceVersionId(), snapshot.versionNumber(), snapshot.code(), snapshot.name(),
                        snapshot.processingDays(), snapshot.formSchema(), snapshot.requirements()),
                app.getFormData(),
                documents.forApplication(id),
                clarifications.findByApplicationIdOrderByCreatedAtAsc(id).stream()
                        .map(c -> new ClarificationItem(c.getId(), c.getQuestion(), c.getDueAt(), c.getResponse(),
                                c.getRespondedAt(), c.getStatus(), c.getCreatedAt())).toList(),
                decisionList.stream()
                        .filter(d -> staff || !"RECOMMENDED".equals(d.getDecision()) && !"RETURNED".equals(d.getDecision()))
                        .map(d -> new DecisionItem(d.getDecision(), d.getReason(),
                                staff ? name(names, d.getDecidedBy()) : "Department", d.getDecidedAt())).toList(),
                historyList.stream().map(h -> new HistoryItem(h.getFromStatus(), h.getToStatus(), h.getCommand(),
                        actorLabel(h.getActorId(), app, names, staff), h.getReason(), h.getOccurredAt())).toList(),
                staff ? allTasks.stream().map(t -> new TaskItem(t.getId(), t.getStepCode(), t.getStatus(), t.getAssigneeId(),
                        name(names, t.getAssigneeId()), t.getDueAt(), t.getOutcome(), t.getCreatedAt(),
                        t.getCompletedAt(), t.getVersion())).toList() : null,
                staff ? commentList.stream().map(c -> new CommentItem(c.getId(), name(names, c.getAuthorId()), c.getBody(),
                        c.getCreatedAt())).toList() : null,
                availableActions(app, actor, allTasks, decisionList));
    }

    @Transactional(readOnly = true)
    public UUID idByReference(String reference, AuthenticatedUser actor) {
        Application app = support.applications.findByReferenceNumber(reference.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Application"));
        return support.loadReadable(app.getId(), actor).getId();
    }

    /** For other modules: the department of an application owned by the actor (404 otherwise). */
    @Transactional(readOnly = true)
    public UUID departmentOfOwnedApplication(UUID applicationId, AuthenticatedUser actor) {
        return support.loadOwned(applicationId, actor).getDepartmentId();
    }

    /** Opens a document of an application the actor may read (owner or in-scope staff). */
    @Transactional
    public DocumentService.Download openDocument(UUID id, UUID documentId, Integer version, AuthenticatedUser actor) {
        support.loadReadable(id, actor);
        if (!id.equals(documents.get(documentId).applicationId())) {
            throw ApiException.notFound("Document");
        }
        return documents.open(documentId, version);
    }

    /** UX hints only: the server re-checks every rule when the action is invoked. */
    List<String> availableActions(Application app, AuthenticatedUser actor, List<WorkflowTask> allTasks,
                                  List<Decision> decisionList) {
        List<String> actions = new ArrayList<>();
        ApplicationStatus s = app.getStatus();
        if (app.isOwnedBy(actor.id())) {
            switch (s) {
                case DRAFT -> actions.addAll(List.of("EDIT", "UPLOAD_DOCUMENT", "SUBMIT", "WITHDRAW"));
                case SUBMITTED -> actions.add("WITHDRAW");
                case CLARIFICATION_REQUIRED -> actions.addAll(List.of("RESPOND_CLARIFICATION", "UPLOAD_DOCUMENT"));
                default -> { }
            }
            return actions;
        }
        if (!support.isStaffViewer(app, actor)) {
            return actions;
        }
        WorkflowTask task = allTasks.stream().filter(WorkflowTask::isActive).reduce((a, b) -> b).orElse(null);
        boolean mine = task != null && task.isAssignedTo(actor.id());
        boolean free = task != null && task.getAssigneeId() == null;
        if ((s == ApplicationStatus.SUBMITTED || s == ApplicationStatus.RESUBMITTED) && (mine || free)
                && actor.hasPermission("TASK:CLAIM")) {
            actions.add("START_REVIEW");
        }
        if (s == ApplicationStatus.UNDER_REVIEW && mine) {
            if (actor.hasPermission("APPLICATION:CLARIFY")) actions.add("REQUEST_CLARIFICATION");
            if (actor.hasPermission("APPLICATION:RECOMMEND")) actions.add("RECOMMEND");
            if (actor.hasPermission("APPLICATION:REJECT")) actions.add("REJECT");
        }
        if (s == ApplicationStatus.PENDING_APPROVAL && (mine || free) && actor.hasPermission("APPLICATION:APPROVE")) {
            UUID recommender = decisionList.stream().filter(d -> "RECOMMENDED".equals(d.getDecision()))
                    .reduce((a, b) -> b).map(Decision::getDecidedBy).orElse(null);
            if (!actor.id().equals(recommender)) {
                actions.addAll(List.of("APPROVE", "RETURN"));
                if (actor.hasPermission("APPLICATION:REJECT")) actions.add("REJECT");
            }
        }
        if ((s == ApplicationStatus.UNDER_REVIEW && mine || s == ApplicationStatus.PENDING_APPROVAL)
                && actor.hasPermission("DOCUMENT:VERIFY")) {
            actions.add("VERIFY_DOCUMENTS");
        }
        if (s == ApplicationStatus.APPROVED && actor.hasPermission("APPLICATION:COMPLETE")) {
            actions.add("COMPLETE");
        }
        if (task != null && actor.hasPermission("TASK:ASSIGN")) {
            actions.add("ASSIGN");
        }
        actions.add("COMMENT");
        return actions;
    }

    Page<Summary> toSummaries(Page<Application> page, boolean staff) {
        List<Application> apps = page.getContent();
        Map<UUID, WorkflowTask> active = support.tasks.findByApplicationIdInAndStatusIn(
                        apps.stream().map(Application::getId).toList(), CaseSupport.ACTIVE_TASK).stream()
                .collect(Collectors.toMap(WorkflowTask::getApplicationId, Function.identity(), (a, b) -> b));
        Map<UUID, String> serviceNames = catalogue.serviceNames(apps.stream().map(Application::getServiceId).distinct().toList());
        Set<UUID> people = new HashSet<>();
        if (staff) {
            apps.forEach(a -> people.add(a.getCitizenId()));
            active.values().forEach(t -> people.add(t.getAssigneeId()));
            people.remove(null);
        }
        Map<UUID, UserSummary> names = users.summaries(people);
        return page.map(a -> summary(a, serviceNames.get(a.getServiceId()), active.get(a.getId()), names, staff));
    }

    private Summary summary(Application a, String serviceName, WorkflowTask task, Map<UUID, UserSummary> names,
                            boolean staff) {
        Instant due = task == null ? null : task.getDueAt();
        boolean overdue = due != null && due.isBefore(support.now()) && !"BLOCKED".equals(task.getStatus());
        return new Summary(a.getId(), a.getReferenceNumber(), a.getStatus().name(), a.getServiceId(), serviceName,
                a.getDepartmentId(), staff ? a.getCitizenId() : null, staff ? name(names, a.getCitizenId()) : null,
                a.getCreatedAt(), a.getSubmittedAt(), a.getDecidedAt(), a.getUpdatedAt(),
                task == null ? null : task.getStepCode(), task == null ? null : task.getStatus(),
                staff && task != null ? task.getAssigneeId() : null,
                staff && task != null ? name(names, task.getAssigneeId()) : null,
                due, overdue, a.getVersion());
    }

    private static String actorLabel(UUID actorId, Application app, Map<UUID, UserSummary> names, boolean staff) {
        if (actorId == null) {
            return "System";
        }
        if (actorId.equals(app.getCitizenId())) {
            return staff ? name(names, actorId) : "You";
        }
        return staff ? name(names, actorId) : "Department";
    }

    private static String name(Map<UUID, UserSummary> names, UUID id) {
        if (id == null) {
            return null;
        }
        UserSummary s = names.get(id);
        return s == null ? null : s.fullName();
    }

    private static Specification<Application> specification(ApplicationFilter f, AuthenticatedUser actor, boolean staff,
                                                            Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (staff) {
                if (!actor.hasGlobalScope()) {
                    if (actor.departmentIds().isEmpty()) {
                        return cb.disjunction();
                    }
                    p.add(root.get("departmentId").in(actor.departmentIds()));
                }
                p.add(cb.notEqual(root.get("status"), ApplicationStatus.DRAFT)); // drafts are private to citizens
            } else {
                p.add(cb.equal(root.get("citizenId"), actor.id()));
            }
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.serviceId() != null) p.add(cb.equal(root.get("serviceId"), f.serviceId()));
            if (f.departmentId() != null) p.add(cb.equal(root.get("departmentId"), f.departmentId()));
            if (f.q() != null && !f.q().isBlank()) {
                p.add(cb.like(cb.upper(root.get("referenceNumber")), "%" + f.q().trim().toUpperCase().replace("%", "") + "%"));
            }
            if (staff && (f.assignedToMe() || f.overdue())) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<WorkflowTask> t = sub.from(WorkflowTask.class);
                List<Predicate> tp = new ArrayList<>();
                tp.add(cb.equal(t.get("applicationId"), root.get("id")));
                tp.add(t.get("status").in(CaseSupport.ACTIVE_TASK));
                if (f.assignedToMe()) tp.add(cb.equal(t.get("assigneeId"), actor.id()));
                if (f.overdue()) {
                    tp.add(cb.lessThan(t.get("dueAt"), now));
                    tp.add(cb.notEqual(t.get("status"), "BLOCKED"));
                }
                sub.select(t.get("id")).where(tp.toArray(Predicate[]::new));
                p.add(cb.exists(sub));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
