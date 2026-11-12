package com.jansetu.modules.application.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.modules.application.domain.Application;
import com.jansetu.modules.application.domain.ApplicationStatus;
import com.jansetu.modules.application.domain.CaseRecords.StatusHistory;
import com.jansetu.modules.application.domain.WorkflowTask;
import com.jansetu.modules.application.repository.ApplicationRepository;
import com.jansetu.modules.application.repository.StatusHistoryRepository;
import com.jansetu.modules.application.repository.WorkflowTaskRepository;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.notification.service.NotificationService;
import com.jansetu.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared building blocks for application use cases: access checks, transitions with history/audit, notifications. */
@Component
class CaseSupport {

    static final String READ_DEPARTMENT = "APPLICATION:READ_DEPARTMENT";
    static final List<String> ACTIVE_TASK = List.of("OPEN", "ASSIGNED", "IN_PROGRESS", "BLOCKED");

    final ApplicationRepository applications;
    final WorkflowTaskRepository tasks;
    final StatusHistoryRepository history;
    final AuditService audit;
    final NotificationService notifications;
    final Clock clock;

    CaseSupport(ApplicationRepository applications, WorkflowTaskRepository tasks, StatusHistoryRepository history,
                AuditService audit, NotificationService notifications, Clock clock) {
        this.applications = applications;
        this.tasks = tasks;
        this.history = history;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    Instant now() {
        return clock.instant();
    }

    Application load(UUID id) {
        return applications.findById(id).orElseThrow(() -> ApiException.notFound("Application"));
    }

    boolean isStaffViewer(Application app, AuthenticatedUser actor) {
        return actor.hasPermission(READ_DEPARTMENT) && actor.canAccessDepartment(app.getDepartmentId());
    }

    /** Owner or in-scope staff; anyone else gets 404 (no enumeration of other citizens' applications). */
    Application loadReadable(UUID id, AuthenticatedUser actor) {
        Application app = load(id);
        if (!app.isOwnedBy(actor.id()) && !isStaffViewer(app, actor)) {
            throw ApiException.notFound("Application");
        }
        return app;
    }

    Application loadOwned(UUID id, AuthenticatedUser actor) {
        Application app = load(id);
        if (!app.isOwnedBy(actor.id())) {
            throw ApiException.notFound("Application");
        }
        return app;
    }

    /** In-scope staff holding {@code permission}. Citizens owning the case still get 403 rather than 404. */
    Application loadForStaff(UUID id, AuthenticatedUser actor, String permission) {
        Application app = load(id);
        if (!actor.canAccessDepartment(app.getDepartmentId())) {
            throw ApiException.notFound("Application");
        }
        actor.requirePermission(permission);
        return app;
    }

    Optional<WorkflowTask> activeTask(UUID applicationId) {
        return tasks.findFirstByApplicationIdAndStatusInOrderByCreatedAtDesc(applicationId, ACTIVE_TASK);
    }

    WorkflowTask requireActiveTask(UUID applicationId, String step) {
        WorkflowTask task = activeTask(applicationId)
                .orElseThrow(() -> ApiException.invalidState("The application has no active task."));
        if (!task.getStepCode().equals(step)) {
            throw ApiException.invalidState("The application is not at the " + step + " step.");
        }
        return task;
    }

    void transition(Application app, ApplicationStatus target, String command, UUID actorId, String reason,
                    boolean citizenVisible) {
        ApplicationStatus from = app.transitionTo(target, now());
        applications.saveAndFlush(app);
        history.save(new StatusHistory(app.getId(), from, target, command, actorId, reason, citizenVisible, now()));
        audit.record(AuditEvent.success("APPLICATION_" + command.toUpperCase(), "APPLICATION", app.getId())
                .detail("from", from.name())
                .detail("to", target.name()));
    }

    void notifyCitizen(Application app, String type, String title, String body) {
        notifications.notify(app.getCitizenId(), "APPLICATION", type, title, body, "APPLICATION", app.getId());
    }

    void notifyStaff(UUID userId, Application app, String type, String title, String body) {
        notifications.notify(userId, "TASK", type, title, body, "APPLICATION", app.getId());
    }

    static String ref(Application app) {
        return app.getReferenceNumber() == null ? "your draft application" : app.getReferenceNumber();
    }

    static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw ApiException.businessRule(what + " is required.");
        }
        return value.trim();
    }
}
