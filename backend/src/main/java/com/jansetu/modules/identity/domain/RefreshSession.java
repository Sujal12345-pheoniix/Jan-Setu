package com.jansetu.modules.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A refresh session (one device/browser login). Only the SHA-256 hash of the opaque refresh token is stored. Each
 * refresh rotates the token: the old row becomes ROTATED and a new ACTIVE row joins the same family. Presenting a
 * ROTATED token again signals theft, and the whole family is revoked.
 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefreshSessionStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected RefreshSession() {
    }

    public RefreshSession(UUID userId, UUID familyId, String tokenHash, Instant now, Instant expiresAt,
                          String userAgent, String ipAddress) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.status = RefreshSessionStatus.ACTIVE;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markRotated(UUID replacement, Instant now) {
        status = RefreshSessionStatus.ROTATED;
        rotatedAt = now;
        lastUsedAt = now;
        replacedById = replacement;
    }

    public void revoke(String reason, Instant now) {
        if (status == RefreshSessionStatus.ACTIVE) {
            status = RefreshSessionStatus.REVOKED;
            revokedAt = now;
            revokeReason = reason;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public RefreshSessionStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getRevokeReason() {
        return revokeReason;
    }
}
