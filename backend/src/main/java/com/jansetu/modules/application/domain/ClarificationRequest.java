package com.jansetu.modules.application.domain;

import com.jansetu.common.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clarification_requests")
public class ClarificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 2000, updatable = false)
    private String question;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @Column(length = 4000)
    private String response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClarificationRequest() {
    }

    public ClarificationRequest(UUID applicationId, UUID requestedBy, String question, Instant dueAt, Instant now) {
        this.applicationId = applicationId;
        this.requestedBy = requestedBy;
        this.question = question;
        this.dueAt = dueAt;
        this.status = "OPEN";
        this.createdAt = now;
    }

    public void respond(String text, Instant now) {
        if (!"OPEN".equals(status)) {
            throw ApiException.invalidState("This clarification request is no longer open.");
        }
        this.response = text;
        this.respondedAt = now;
        this.status = "RESPONDED";
    }

    public UUID getId() { return id; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getQuestion() { return question; }
    public Instant getDueAt() { return dueAt; }
    public String getResponse() { return response; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
