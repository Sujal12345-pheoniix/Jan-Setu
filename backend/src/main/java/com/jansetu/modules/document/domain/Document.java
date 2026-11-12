package com.jansetu.modules.document.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Document metadata. Bytes live in private storage, one {@link DocumentVersion} per upload. */
@Entity
@Table(name = "documents")
public class Document extends AuditableEntity {

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Column(name = "grievance_id", updatable = false)
    private UUID grievanceId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "requirement_code", length = 50, updatable = false)
    private String requirementCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    protected Document() {
    }

    public static Document forApplication(UUID applicationId, UUID ownerId, String requirementCode) {
        Document d = new Document();
        d.applicationId = applicationId;
        d.ownerId = ownerId;
        d.requirementCode = requirementCode;
        d.status = DocumentStatus.AVAILABLE;
        d.currentVersion = 0;
        return d;
    }

    public static Document forGrievance(UUID grievanceId, UUID ownerId) {
        Document d = new Document();
        d.grievanceId = grievanceId;
        d.ownerId = ownerId;
        d.status = DocumentStatus.AVAILABLE;
        d.currentVersion = 0;
        return d;
    }

    /** A new upload replaces the content and resets verification. */
    public int nextVersion() {
        if (status == DocumentStatus.VERIFIED) {
            throw ApiException.invalidState("A verified document cannot be replaced.");
        }
        currentVersion++;
        status = DocumentStatus.AVAILABLE;
        verifiedBy = null;
        verifiedAt = null;
        rejectionReason = null;
        return currentVersion;
    }

    public void verify(UUID actor, Instant now) {
        if (status != DocumentStatus.AVAILABLE && status != DocumentStatus.REJECTED) {
            throw ApiException.invalidState("Only an available document can be verified.");
        }
        status = DocumentStatus.VERIFIED;
        verifiedBy = actor;
        verifiedAt = now;
        rejectionReason = null;
    }

    public void reject(UUID actor, Instant now, String reason) {
        if (status != DocumentStatus.AVAILABLE && status != DocumentStatus.VERIFIED) {
            throw ApiException.invalidState("Only an available document can be rejected.");
        }
        status = DocumentStatus.REJECTED;
        verifiedBy = actor;
        verifiedAt = now;
        rejectionReason = reason;
    }

    public boolean isDownloadable() {
        return status == DocumentStatus.AVAILABLE || status == DocumentStatus.VERIFIED || status == DocumentStatus.REJECTED;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getGrievanceId() {
        return grievanceId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
