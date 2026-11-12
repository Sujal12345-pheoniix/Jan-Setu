package com.jansetu.modules.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "detected_media_type", nullable = false, length = 100)
    private String detectedMediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 100)
    private String checksum;

    @Column(name = "scan_status", nullable = false, length = 20)
    private String scanStatus;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected DocumentVersion() {
    }

    public DocumentVersion(UUID documentId, int versionNumber, String storageKey, String originalFilename,
                           String detectedMediaType, long sizeBytes, String checksum, UUID uploadedBy, Instant uploadedAt) {
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.detectedMediaType = detectedMediaType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.scanStatus = "SKIPPED";
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDetectedMediaType() {
        return detectedMediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
