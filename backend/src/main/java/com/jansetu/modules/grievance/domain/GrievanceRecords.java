package com.jansetu.modules.grievance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only grievance records. Internal comments are never returned to citizens. */
public final class GrievanceRecords {

    private GrievanceRecords() {
    }

    @Entity
    @Immutable
    @Table(name = "grievance_comments")
    public static class GrievanceComment {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        @Column(name = "grievance_id", nullable = false)
        private UUID grievanceId;
        @Column(name = "author_id", nullable = false)
        private UUID authorId;
        @Column(nullable = false, length = 20)
        private String visibility;
        @Column(nullable = false, length = 4000)
        private String body;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected GrievanceComment() {
        }

        public GrievanceComment(UUID grievanceId, UUID authorId, String visibility, String body, Instant createdAt) {
            this.grievanceId = grievanceId;
            this.authorId = authorId;
            this.visibility = visibility;
            this.body = body;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public UUID getAuthorId() { return authorId; }
        public String getVisibility() { return visibility; }
        public String getBody() { return body; }
        public Instant getCreatedAt() { return createdAt; }
    }

    @Entity
    @Immutable
    @Table(name = "grievance_status_history")
    public static class GrievanceHistory {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        @Column(name = "grievance_id", nullable = false)
        private UUID grievanceId;
        @Column(name = "from_status", length = 30)
        private String fromStatus;
        @Column(name = "to_status", nullable = false, length = 30)
        private String toStatus;
        @Column(name = "actor_id")
        private UUID actorId;
        @Column(length = 2000)
        private String note;
        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected GrievanceHistory() {
        }

        public GrievanceHistory(UUID grievanceId, GrievanceStatus from, GrievanceStatus to, UUID actorId, String note,
                                Instant occurredAt) {
            this.grievanceId = grievanceId;
            this.fromStatus = from == null ? null : from.name();
            this.toStatus = to.name();
            this.actorId = actorId;
            this.note = note;
            this.occurredAt = occurredAt;
        }

        public String getFromStatus() { return fromStatus; }
        public String getToStatus() { return toStatus; }
        public UUID getActorId() { return actorId; }
        public String getNote() { return note; }
        public Instant getOccurredAt() { return occurredAt; }
    }

    @Entity
    @Immutable
    @Table(name = "grievance_feedback")
    public static class GrievanceFeedback {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;
        @Column(name = "grievance_id", nullable = false)
        private UUID grievanceId;
        @Column(nullable = false)
        private short rating;
        @Column(length = 2000)
        private String comment;
        @Column(name = "submitted_at", nullable = false)
        private Instant submittedAt;

        protected GrievanceFeedback() {
        }

        public GrievanceFeedback(UUID grievanceId, int rating, String comment, Instant submittedAt) {
            this.grievanceId = grievanceId;
            this.rating = (short) rating;
            this.comment = comment;
            this.submittedAt = submittedAt;
        }

        public int getRating() { return rating; }
        public String getComment() { return comment; }
        public Instant getSubmittedAt() { return submittedAt; }
    }
}
