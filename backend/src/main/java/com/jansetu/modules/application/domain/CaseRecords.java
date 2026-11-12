package com.jansetu.modules.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only case records: status history, decisions and internal comments. */
public final class CaseRecords {

    private CaseRecords() {
    }

    @Entity
    @Immutable
    @Table(name = "application_status_history")
    public static class StatusHistory {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        @Column(name = "application_id", nullable = false)
        private UUID applicationId;
        @Column(name = "from_status", length = 30)
        private String fromStatus;
        @Column(name = "to_status", nullable = false, length = 30)
        private String toStatus;
        @Column(nullable = false, length = 50)
        private String command;
        @Column(name = "actor_id")
        private UUID actorId;
        @Column(length = 2000)
        private String reason;
        @Column(name = "citizen_visible", nullable = false)
        private boolean citizenVisible;
        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected StatusHistory() {
        }

        public StatusHistory(UUID applicationId, ApplicationStatus from, ApplicationStatus to, String command,
                             UUID actorId, String reason, boolean citizenVisible, Instant occurredAt) {
            this.applicationId = applicationId;
            this.fromStatus = from == null ? null : from.name();
            this.toStatus = to.name();
            this.command = command;
            this.actorId = actorId;
            this.reason = reason;
            this.citizenVisible = citizenVisible;
            this.occurredAt = occurredAt;
        }

        public UUID getId() { return id; }
        public String getFromStatus() { return fromStatus; }
        public String getToStatus() { return toStatus; }
        public String getCommand() { return command; }
        public UUID getActorId() { return actorId; }
        public String getReason() { return reason; }
        public boolean isCitizenVisible() { return citizenVisible; }
        public Instant getOccurredAt() { return occurredAt; }
    }

    @Entity
    @Immutable
    @Table(name = "application_decisions")
    public static class Decision {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        @Column(name = "application_id", nullable = false)
        private UUID applicationId;
        @Column(nullable = false, length = 30)
        private String decision;
        @Column(name = "reason_code", length = 50)
        private String reasonCode;
        @Column(length = 2000)
        private String reason;
        @Column(name = "decided_by", nullable = false)
        private UUID decidedBy;
        @Column(name = "decided_at", nullable = false)
        private Instant decidedAt;

        protected Decision() {
        }

        public Decision(UUID applicationId, String decision, String reason, UUID decidedBy, Instant decidedAt) {
            this.applicationId = applicationId;
            this.decision = decision;
            this.reason = reason;
            this.decidedBy = decidedBy;
            this.decidedAt = decidedAt;
        }

        public UUID getId() { return id; }
        public String getDecision() { return decision; }
        public String getReason() { return reason; }
        public UUID getDecidedBy() { return decidedBy; }
        public Instant getDecidedAt() { return decidedAt; }
    }

    @Entity
    @Immutable
    @Table(name = "application_comments")
    public static class Comment {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        @Column(name = "application_id", nullable = false)
        private UUID applicationId;
        @Column(name = "author_id", nullable = false)
        private UUID authorId;
        @Column(nullable = false, length = 20)
        private String visibility;
        @Column(nullable = false, length = 4000)
        private String body;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected Comment() {
        }

        public Comment(UUID applicationId, UUID authorId, String body, Instant createdAt) {
            this.applicationId = applicationId;
            this.authorId = authorId;
            this.visibility = "INTERNAL";
            this.body = body;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public UUID getAuthorId() { return authorId; }
        public String getVisibility() { return visibility; }
        public String getBody() { return body; }
        public Instant getCreatedAt() { return createdAt; }
    }
}
