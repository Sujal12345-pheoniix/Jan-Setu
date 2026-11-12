package com.jansetu.modules.grievance.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.modules.application.service.ApplicationQueryService;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.department.service.DepartmentService;
import com.jansetu.modules.document.service.DocumentService;
import com.jansetu.modules.document.service.DocumentService.DocumentView;
import com.jansetu.modules.grievance.domain.Grievance;
import com.jansetu.modules.grievance.domain.GrievanceRecords.GrievanceComment;
import com.jansetu.modules.grievance.domain.GrievanceRecords.GrievanceFeedback;
import com.jansetu.modules.grievance.domain.GrievanceRecords.GrievanceHistory;
import com.jansetu.modules.grievance.domain.GrievanceStatus;
import com.jansetu.modules.grievance.repository.GrievanceCommentRepository;
import com.jansetu.modules.grievance.repository.GrievanceFeedbackRepository;
import com.jansetu.modules.grievance.repository.GrievanceHistoryRepository;
import com.jansetu.modules.grievance.repository.GrievanceRepository;
import com.jansetu.modules.identity.service.UserDirectoryService;
import com.jansetu.modules.identity.service.UserDirectoryService.UserSummary;
import com.jansetu.modules.notification.service.NotificationService;
import com.jansetu.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grievance lifecycle. Citizens see their own grievances and public replies only; staff see grievances of their
 * departments, including internal notes. Every transition is validated by {@link GrievanceStatus}.
 */
@Service
public class GrievanceService {

    public static final Set<String> CATEGORIES = Set.of("SERVICE_DELAY", "STAFF_CONDUCT", "INCORRECT_DECISION",
            "DOCUMENT_ISSUE", "PORTAL_ISSUE", "OTHER");
    private static final String READ_DEPARTMENT = "GRIEVANCE:READ_DEPARTMENT";

    public record CreateCommand(UUID departmentId, String category, String subject, String description,
                                UUID applicationId) {
    }

    public record Summary(UUID id, String referenceNumber, String status, String category, String subject,
                          UUID departmentId, String departmentName, UUID assigneeId, String assigneeName,
                          String citizenName, Instant createdAt, Instant updatedAt, long version) {
    }

    public record CommentItem(UUID id, String visibility, String authorName, boolean byCitizen, String body,
                              Instant createdAt) {
    }

    public record HistoryItem(String fromStatus, String toStatus, String actorName, String note, Instant occurredAt) {
    }

    public record FeedbackItem(int rating, String comment, Instant submittedAt) {
    }

    public record Detail(Summary summary, String description, UUID applicationId, String resolutionSummary,
                         Instant acknowledgedAt, Instant resolvedAt, Instant closedAt, Instant reopenDeadline,
                         int reopenCount, List<CommentItem> comments, List<HistoryItem> history,
                         List<DocumentView> documents, FeedbackItem feedback, List<String> availableActions) {
    }

    public record Filter(GrievanceStatus status, String category, UUID departmentId, boolean assignedToMe, String q) {
    }

    private final GrievanceRepository grievances;
    private final GrievanceCommentRepository comments;
    private final GrievanceHistoryRepository history;
    private final GrievanceFeedbackRepository feedback;
    private final DepartmentService departments;
    private final ApplicationQueryService applications;
    private final DocumentService documents;
    private final UserDirectoryService users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public GrievanceService(GrievanceRepository grievances, GrievanceCommentRepository comments,
                            GrievanceHistoryRepository history, GrievanceFeedbackRepository feedback,
                            DepartmentService departments, ApplicationQueryService applications,
                            DocumentService documents, UserDirectoryService users, NotificationService notifications,
                            AuditService audit, Clock clock) {
        this.grievances = grievances;
        this.comments = comments;
        this.history = history;
        this.feedback = feedback;
        this.departments = departments;
        this.applications = applications;
        this.documents = documents;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- Citizen ----------------------------------------------------------------------------------------------

    @Transactional
    public UUID create(CreateCommand command, AuthenticatedUser actor) {
        actor.requirePermission("GRIEVANCE:CREATE");
        if (!CATEGORIES.contains(command.category())) {
            throw ApiException.businessRule("Unknown grievance category.");
        }
        UUID departmentId = command.departmentId();
        if (command.applicationId() != null) {
            UUID appDepartment = applications.departmentOfOwnedApplication(command.applicationId(), actor);
            if (departmentId == null) {
                departmentId = appDepartment;
            } else if (!departmentId.equals(appDepartment)) {
                throw ApiException.businessRule("The linked application belongs to a different department.");
            }
        }
        if (departmentId == null) {
            throw ApiException.businessRule("Select a department or link an application.");
        }
        departments.requireActive(departmentId);
        String reference = String.format("GRV-%d-%06d", Year.now(ZoneOffset.UTC).getValue(), grievances.nextReferenceValue());
        Grievance g = grievances.saveAndFlush(new Grievance(reference, actor.id(), departmentId, command.applicationId(),
                command.category(), command.subject().trim(), command.description().trim()));
        history.save(new GrievanceHistory(g.getId(), null, GrievanceStatus.SUBMITTED, actor.id(), null, now()));
        audit.record(AuditEvent.success("GRIEVANCE_SUBMITTED", "GRIEVANCE", g.getId()));
        notifications.notify(actor.id(), "GRIEVANCE", "GRIEVANCE_SUBMITTED", "Grievance registered",
                "Your grievance " + reference + " has been registered.", "GRIEVANCE", g.getId());
        return g.getId();
    }

    @Transactional
    public void reopen(UUID id, String reason, AuthenticatedUser actor) {
        Grievance g = loadOwned(id, actor);
        if (!g.canReopen(now())) {
            throw ApiException.businessRule("This grievance can no longer be reopened (window "
                    + Grievance.REOPEN_WINDOW.toDays() + " days, at most " + Grievance.MAX_REOPENS + " time).");
        }
        String text = requireText(reason, "A reason for reopening");
        transition(g, GrievanceStatus.REOPENED, actor, text);
        comments.save(new GrievanceComment(id, actor.id(), "PUBLIC", text, now()));
        notifications.notify(g.getAssigneeId(), "GRIEVANCE", "GRIEVANCE_REOPENED", "Grievance reopened",
                g.getReferenceNumber() + " was reopened by the citizen.", "GRIEVANCE", id);
    }

    @Transactional
    public void close(UUID id, AuthenticatedUser actor) {
        Grievance g = grievances.findById(id).orElseThrow(() -> ApiException.notFound("Grievance"));
        if (g.isOwnedBy(actor.id())) {
            transition(g, GrievanceStatus.CLOSED, actor, "Closed by citizen");
            return;
        }
        requireStaff(g, actor, "GRIEVANCE:RESOLVE");
        if (g.getStatus() == GrievanceStatus.RESOLVED && g.getReopenDeadline() != null && now().isBefore(g.getReopenDeadline())) {
            throw ApiException.businessRule("Staff can close a resolved grievance only after the reopen window ends.");
        }
        transition(g, GrievanceStatus.CLOSED, actor, "Closed by department");
    }

    @Transactional
    public void submitFeedback(UUID id, int rating, String comment, AuthenticatedUser actor) {
        Grievance g = loadOwned(id, actor);
        if (g.getStatus() != GrievanceStatus.RESOLVED && g.getStatus() != GrievanceStatus.CLOSED) {
            throw ApiException.invalidState("Feedback can be given once the grievance is resolved.");
        }
        if (feedback.findByGrievanceId(id).isPresent()) {
            throw ApiException.duplicate("Feedback was already submitted for this grievance.");
        }
        if (rating < 1 || rating > 5) {
            throw ApiException.businessRule("Rating must be between 1 and 5.");
        }
        feedback.save(new GrievanceFeedback(id, rating, blankToNull(comment), now()));
    }

    @Transactional
    public DocumentView uploadEvidence(UUID id, DocumentService.UploadCommand file, AuthenticatedUser actor) {
        actor.requirePermission("DOCUMENT:UPLOAD");
        Grievance g = loadOwned(id, actor);
        if (g.getStatus() == GrievanceStatus.CLOSED) {
            throw ApiException.invalidState("Evidence cannot be added to a closed grievance.");
        }
        return documents.uploadForGrievance(id, actor.id(), file, actor.id());
    }

    // ---- Staff ------------------------------------------------------------------------------------------------

    @Transactional
    public void acknowledge(UUID id, AuthenticatedUser actor) {
        Grievance g = loadForStaff(id, actor);
        if (!actor.hasPermission("GRIEVANCE:RESPOND") && !actor.hasPermission("GRIEVANCE:ASSIGN")) {
            throw ApiException.accessDenied("You do not have permission to perform this action.");
        }
        transition(g, GrievanceStatus.ACKNOWLEDGED, actor, null);
        notifyCitizen(g, "GRIEVANCE_ACKNOWLEDGED", "Grievance acknowledged",
                "Your grievance " + g.getReferenceNumber() + " has been acknowledged by the department.");
    }

    @Transactional
    public void assign(UUID id, UUID assigneeId, AuthenticatedUser actor) {
        Grievance g = requireStaff(loadForStaff(id, actor), actor, "GRIEVANCE:ASSIGN");
        users.requireEligibleAssignee(assigneeId, g.getDepartmentId(), "GRIEVANCE:RESPOND");
        g.assignTo(assigneeId);
        transition(g, GrievanceStatus.ASSIGNED, actor, null);
        notifications.notify(assigneeId, "TASK", "GRIEVANCE_ASSIGNED", "Grievance assigned",
                g.getReferenceNumber() + " was assigned to you.", "GRIEVANCE", id);
    }

    @Transactional
    public void start(UUID id, AuthenticatedUser actor) {
        Grievance g = requireStaff(loadForStaff(id, actor), actor, "GRIEVANCE:RESPOND");
        if (!g.isAssignedTo(actor.id())) {
            throw ApiException.accessDenied("Only the assigned officer can start work on this grievance.");
        }
        transition(g, GrievanceStatus.IN_PROGRESS, actor, null);
    }

    @Transactional
    public void resolve(UUID id, String summary, AuthenticatedUser actor) {
        Grievance g = requireStaff(loadForStaff(id, actor), actor, "GRIEVANCE:RESOLVE");
        if (!g.isAssignedTo(actor.id())) {
            throw ApiException.accessDenied("Only the assigned officer can resolve this grievance.");
        }
        String text = requireText(summary, "A resolution summary");
        g.resolveWith(text);
        transition(g, GrievanceStatus.RESOLVED, actor, text);
        comments.save(new GrievanceComment(id, actor.id(), "PUBLIC", text, now()));
        notifyCitizen(g, "GRIEVANCE_RESOLVED", "Grievance resolved",
                "Your grievance " + g.getReferenceNumber() + " has been resolved. You can reopen it within "
                        + Grievance.REOPEN_WINDOW.toDays() + " days if the issue persists.");
    }

    // ---- Comments ---------------------------------------------------------------------------------------------

    /** Staff may add INTERNAL notes or PUBLIC replies; citizens may add PUBLIC comments to open grievances. */
    @Transactional
    public void comment(UUID id, String visibility, String body, AuthenticatedUser actor) {
        Grievance g = grievances.findById(id).orElseThrow(() -> ApiException.notFound("Grievance"));
        String text = requireText(body, "A comment");
        if (g.isOwnedBy(actor.id())) {
            if (!"PUBLIC".equals(visibility)) {
                throw ApiException.accessDenied("Citizens can only add public comments.");
            }
            if (g.getStatus() == GrievanceStatus.CLOSED) {
                throw ApiException.invalidState("Comments are closed for this grievance.");
            }
            comments.save(new GrievanceComment(id, actor.id(), "PUBLIC", text, now()));
            notifications.notify(g.getAssigneeId(), "GRIEVANCE", "GRIEVANCE_CITIZEN_COMMENT", "New citizen comment",
                    "The citizen commented on " + g.getReferenceNumber() + ".", "GRIEVANCE", id);
            return;
        }
        requireStaff(loadForStaff(id, actor), actor, "GRIEVANCE:RESPOND");
        if (!"PUBLIC".equals(visibility) && !"INTERNAL".equals(visibility)) {
            throw ApiException.businessRule("Visibility must be PUBLIC or INTERNAL.");
        }
        comments.save(new GrievanceComment(id, actor.id(), visibility, text, now()));
        audit.record(AuditEvent.success("GRIEVANCE_COMMENT_ADDED", "GRIEVANCE", id).detail("visibility", visibility));
        if ("PUBLIC".equals(visibility)) {
            notifyCitizen(g, "GRIEVANCE_REPLY", "New reply on your grievance",
                    "The department replied on " + g.getReferenceNumber() + ".");
        }
    }

    // ---- Queries ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Summary> list(Filter filter, PageQuery pageQuery, AuthenticatedUser actor) {
        boolean staff = actor.hasPermission(READ_DEPARTMENT);
        if (!staff && !actor.hasPermission("GRIEVANCE:READ_OWN")) {
            throw ApiException.accessDenied("You do not have permission to view grievances.");
        }
        if (staff && filter.departmentId() != null) {
            actor.requireDepartmentScope(filter.departmentId(), "Department");
        }
        Page<Grievance> page = grievances.findAll(spec(filter, actor, staff),
                pageQuery.toPageable(Map.of("createdAt", "createdAt", "updatedAt", "updatedAt", "status", "status"),
                        Sort.by(Sort.Direction.DESC, "updatedAt")));
        Set<UUID> people = new HashSet<>();
        page.getContent().forEach(g -> {
            people.add(g.getAssigneeId());
            people.add(g.getCitizenId());
        });
        people.remove(null);
        Map<UUID, UserSummary> names = staff ? users.summaries(people) : Map.of();
        var depts = departments.summaries(page.getContent().stream().map(Grievance::getDepartmentId).distinct().toList());
        return page.map(g -> summary(g, names, depts, staff));
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id, AuthenticatedUser actor) {
        Grievance g = grievances.findById(id).orElseThrow(() -> ApiException.notFound("Grievance"));
        boolean owner = g.isOwnedBy(actor.id());
        boolean staff = !owner && actor.hasPermission(READ_DEPARTMENT) && actor.canAccessDepartment(g.getDepartmentId());
        if (!owner && !staff) {
            throw ApiException.notFound("Grievance");
        }
        List<GrievanceComment> commentList = staff ? comments.findByGrievanceIdOrderByCreatedAtAsc(id)
                : comments.findByGrievanceIdAndVisibilityOrderByCreatedAtAsc(id, "PUBLIC");
        List<GrievanceHistory> historyList = history.findByGrievanceIdOrderByOccurredAtAsc(id);
        Set<UUID> people = new HashSet<>();
        people.add(g.getAssigneeId());
        people.add(g.getCitizenId());
        commentList.forEach(c -> people.add(c.getAuthorId()));
        historyList.forEach(h -> people.add(h.getActorId()));
        people.remove(null);
        Map<UUID, UserSummary> names = users.summaries(people);
        var depts = departments.summaries(List.of(g.getDepartmentId()));
        return new Detail(summary(g, names, depts, staff), g.getDescription(), g.getApplicationId(),
                g.getResolutionSummary(), g.getAcknowledgedAt(), g.getResolvedAt(), g.getClosedAt(),
                g.getReopenDeadline(), g.getReopenCount(),
                commentList.stream().map(c -> new CommentItem(c.getId(), c.getVisibility(),
                        label(c.getAuthorId(), g, names, staff), c.getAuthorId().equals(g.getCitizenId()), c.getBody(),
                        c.getCreatedAt())).toList(),
                historyList.stream().map(h -> new HistoryItem(h.getFromStatus(), h.getToStatus(),
                        label(h.getActorId(), g, names, staff), staff ? h.getNote() : publicNote(h), h.getOccurredAt())).toList(),
                documents.forGrievance(id),
                feedback.findByGrievanceId(id).map(f -> new FeedbackItem(f.getRating(), f.getComment(), f.getSubmittedAt()))
                        .orElse(null),
                actions(g, actor, owner, staff));
    }

    @Transactional
    public DocumentService.Download openEvidence(UUID id, UUID documentId, AuthenticatedUser actor) {
        detail(id, actor); // access check
        if (!id.equals(documents.get(documentId).grievanceId())) {
            throw ApiException.notFound("Document");
        }
        return documents.open(documentId, null);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private List<String> actions(Grievance g, AuthenticatedUser actor, boolean owner, boolean staff) {
        List<String> a = new ArrayList<>();
        GrievanceStatus s = g.getStatus();
        if (owner) {
            if (s != GrievanceStatus.CLOSED) a.add("COMMENT");
            if (s != GrievanceStatus.CLOSED && s != GrievanceStatus.RESOLVED) a.add("UPLOAD_EVIDENCE");
            if (g.canReopen(now())) a.add("REOPEN");
            if (s == GrievanceStatus.RESOLVED) a.add("CLOSE");
            if ((s == GrievanceStatus.RESOLVED || s == GrievanceStatus.CLOSED) && feedback.findByGrievanceId(g.getId()).isEmpty()) {
                a.add("FEEDBACK");
            }
            return a;
        }
        if (!staff) {
            return a;
        }
        if (s == GrievanceStatus.SUBMITTED) a.add("ACKNOWLEDGE");
        if (actor.hasPermission("GRIEVANCE:ASSIGN") && g.getStatus().canTransitionTo(GrievanceStatus.ASSIGNED)) a.add("ASSIGN");
        if (g.isAssignedTo(actor.id()) && s.canTransitionTo(GrievanceStatus.IN_PROGRESS)) a.add("START");
        if (g.isAssignedTo(actor.id()) && s == GrievanceStatus.IN_PROGRESS && actor.hasPermission("GRIEVANCE:RESOLVE")) a.add("RESOLVE");
        if (actor.hasPermission("GRIEVANCE:RESPOND") && s != GrievanceStatus.CLOSED) a.add("COMMENT");
        if (s == GrievanceStatus.RESOLVED && actor.hasPermission("GRIEVANCE:RESOLVE")
                && g.getReopenDeadline() != null && !now().isBefore(g.getReopenDeadline())) a.add("CLOSE");
        return a;
    }

    private void transition(Grievance g, GrievanceStatus target, AuthenticatedUser actor, String note) {
        GrievanceStatus from = g.moveTo(target, now());
        grievances.saveAndFlush(g);
        history.save(new GrievanceHistory(g.getId(), from, target, actor.id(), note, now()));
        audit.record(AuditEvent.success("GRIEVANCE_" + target.name(), "GRIEVANCE", g.getId()).detail("from", from.name()));
    }

    private Grievance loadOwned(UUID id, AuthenticatedUser actor) {
        Grievance g = grievances.findById(id).orElseThrow(() -> ApiException.notFound("Grievance"));
        if (!g.isOwnedBy(actor.id())) {
            throw ApiException.notFound("Grievance");
        }
        return g;
    }

    private Grievance loadForStaff(UUID id, AuthenticatedUser actor) {
        Grievance g = grievances.findById(id).orElseThrow(() -> ApiException.notFound("Grievance"));
        if (!actor.canAccessDepartment(g.getDepartmentId()) || !actor.hasPermission(READ_DEPARTMENT)) {
            throw ApiException.notFound("Grievance");
        }
        return g;
    }

    private static Grievance requireStaff(Grievance g, AuthenticatedUser actor, String permission) {
        actor.requirePermission(permission);
        return g;
    }

    private void notifyCitizen(Grievance g, String type, String title, String body) {
        notifications.notify(g.getCitizenId(), "GRIEVANCE", type, title, body, "GRIEVANCE", g.getId());
    }

    private Summary summary(Grievance g, Map<UUID, UserSummary> names,
                            Map<UUID, DepartmentService.DepartmentSummary> depts, boolean staff) {
        var dept = depts.get(g.getDepartmentId());
        UserSummary assignee = g.getAssigneeId() == null ? null : names.get(g.getAssigneeId());
        UserSummary citizen = names.get(g.getCitizenId());
        return new Summary(g.getId(), g.getReferenceNumber(), g.getStatus().name(), g.getCategory(), g.getSubject(),
                g.getDepartmentId(), dept == null ? null : dept.name(), staff ? g.getAssigneeId() : null,
                staff && assignee != null ? assignee.fullName() : null, staff && citizen != null ? citizen.fullName() : null,
                g.getCreatedAt(), g.getUpdatedAt(), g.getVersion());
    }

    private static String label(UUID actorId, Grievance g, Map<UUID, UserSummary> names, boolean staff) {
        if (actorId == null) {
            return "System";
        }
        if (actorId.equals(g.getCitizenId())) {
            return staff ? nameOf(names, actorId) : "You";
        }
        return staff ? nameOf(names, actorId) : "Department";
    }

    private static String nameOf(Map<UUID, UserSummary> names, UUID id) {
        UserSummary s = names.get(id);
        return s == null ? null : s.fullName();
    }

    private static String publicNote(GrievanceHistory h) {
        return "RESOLVED".equals(h.getToStatus()) || "REOPENED".equals(h.getToStatus()) ? h.getNote() : null;
    }

    private static Specification<Grievance> spec(Filter f, AuthenticatedUser actor, boolean staff) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (staff) {
                if (!actor.hasGlobalScope()) {
                    if (actor.departmentIds().isEmpty()) {
                        return cb.disjunction();
                    }
                    p.add(root.get("departmentId").in(actor.departmentIds()));
                }
                if (f.assignedToMe()) p.add(cb.equal(root.get("assigneeId"), actor.id()));
            } else {
                p.add(cb.equal(root.get("citizenId"), actor.id()));
            }
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.category() != null && !f.category().isBlank()) p.add(cb.equal(root.get("category"), f.category()));
            if (f.departmentId() != null) p.add(cb.equal(root.get("departmentId"), f.departmentId()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toUpperCase().replace("%", "") + "%";
                p.add(cb.or(cb.like(cb.upper(root.get("referenceNumber")), like), cb.like(cb.upper(root.get("subject")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private Instant now() {
        return clock.instant();
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw ApiException.businessRule(what + " is required.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
