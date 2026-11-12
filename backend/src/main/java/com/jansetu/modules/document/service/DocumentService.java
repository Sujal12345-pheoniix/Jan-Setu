package com.jansetu.modules.document.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.integration.storage.DocumentStoragePort;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.modules.document.domain.Document;
import com.jansetu.modules.document.domain.DocumentStatus;
import com.jansetu.modules.document.domain.DocumentVersion;
import com.jansetu.modules.document.repository.DocumentRepository;
import com.jansetu.modules.document.repository.DocumentVersionRepository;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Public API of the document module: validated storage and metadata. It has no HTTP endpoints of its own;
 * the owning modules (applications, grievances) authorise access to their documents before calling it.
 */
@Service
public class DocumentService {

    public record VersionView(int versionNumber, String fileName, String mediaType, long sizeBytes, Instant uploadedAt) {
    }

    public record DocumentView(UUID id, UUID applicationId, UUID grievanceId, UUID ownerId, String requirementCode,
                               String status, int currentVersion, String fileName, String mediaType, long sizeBytes,
                               String checksum, Instant uploadedAt, String rejectionReason, Instant verifiedAt,
                               List<VersionView> versions) {
    }

    public record Download(String fileName, String mediaType, long sizeBytes, InputStream content) {
    }

    public record UploadCommand(String originalFilename, String declaredType, byte[] content) {
    }

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentStoragePort storage;
    private final FileValidator validator;
    private final AuditService auditService;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                           DocumentStoragePort storage, FileValidator validator, AuditService auditService, Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.storage = storage;
        this.validator = validator;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Uploads (or replaces) the document for an application requirement. */
    @Transactional
    public DocumentView uploadForApplication(UUID applicationId, UUID ownerId, String requirementCode,
                                             String allowedTypes, Long maxBytes, UploadCommand file, UUID actorId) {
        FileValidator.ValidatedFile valid = validator.validate(file.originalFilename(), file.declaredType(),
                file.content(), allowedTypes, maxBytes);
        Document document = documents.findByApplicationIdAndRequirementCode(applicationId, requirementCode)
                .orElseGet(() -> documents.saveAndFlush(Document.forApplication(applicationId, ownerId, requirementCode)));
        return store(document, valid, actorId);
    }

    @Transactional
    public DocumentView uploadForGrievance(UUID grievanceId, UUID ownerId, UploadCommand file, UUID actorId) {
        FileValidator.ValidatedFile valid = validator.validate(file.originalFilename(), file.declaredType(),
                file.content(), null, null);
        Document document = documents.saveAndFlush(Document.forGrievance(grievanceId, ownerId));
        return store(document, valid, actorId);
    }

    private DocumentView store(Document document, FileValidator.ValidatedFile valid, UUID actorId) {
        int version = document.nextVersion();
        UUID keyId = UUID.randomUUID();
        String key = keyId.toString().substring(0, 2) + "/" + keyId;
        storage.put(key, valid.content());
        removeOnRollback(key);
        documents.saveAndFlush(document);
        versions.saveAndFlush(new DocumentVersion(document.getId(), version, key, valid.safeFilename(), valid.mediaType(),
                valid.content().length, "sha256:" + sha256(valid.content()), actorId, clock.instant()));
        auditService.record(AuditEvent.success("DOCUMENT_UPLOADED", "DOCUMENT", document.getId())
                .detail("version", version)
                .detail("mediaType", valid.mediaType())
                .detail("sizeBytes", valid.content().length));
        return view(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> forApplication(UUID applicationId) {
        return views(documents.findByApplicationIdOrderByRequirementCodeAsc(applicationId));
    }

    @Transactional(readOnly = true)
    public List<DocumentView> forGrievance(UUID grievanceId) {
        return views(documents.findByGrievanceIdOrderByCreatedAtAsc(grievanceId));
    }

    @Transactional(readOnly = true)
    public DocumentView get(UUID documentId) {
        return view(load(documentId));
    }

    /** Opens the bytes of a version (latest when {@code versionNumber} is null). Caller has authorised access. */
    @Transactional
    public Download open(UUID documentId, Integer versionNumber) {
        Document document = load(documentId);
        if (!document.isDownloadable()) {
            throw ApiException.invalidState("This document is not available for download yet.");
        }
        int number = versionNumber == null ? document.getCurrentVersion() : versionNumber;
        DocumentVersion version = versions.findByDocumentIdAndVersionNumber(documentId, number)
                .orElseThrow(() -> ApiException.notFound("Document version"));
        auditService.record(AuditEvent.success("DOCUMENT_DOWNLOADED", "DOCUMENT", documentId).detail("version", number));
        return new Download(version.getOriginalFilename(), version.getDetectedMediaType(), version.getSizeBytes(),
                storage.get(version.getStorageKey()));
    }

    @Transactional
    public DocumentView verify(UUID documentId, UUID actorId) {
        Document document = load(documentId);
        document.verify(actorId, clock.instant());
        documents.saveAndFlush(document);
        auditService.record(AuditEvent.success("DOCUMENT_VERIFIED", "DOCUMENT", documentId));
        return view(document);
    }

    @Transactional
    public DocumentView reject(UUID documentId, UUID actorId, String reason) {
        Document document = load(documentId);
        document.reject(actorId, clock.instant(), reason);
        documents.saveAndFlush(document);
        auditService.record(AuditEvent.success("DOCUMENT_REJECTED", "DOCUMENT", documentId));
        return view(document);
    }

    /** Requirement codes of this application whose documents are present and not rejected. */
    @Transactional(readOnly = true)
    public Map<String, DocumentStatus> requirementStatuses(UUID applicationId) {
        return documents.findByApplicationIdOrderByRequirementCodeAsc(applicationId).stream()
                .filter(d -> d.getRequirementCode() != null)
                .collect(Collectors.toMap(Document::getRequirementCode, Document::getStatus));
    }

    private Document load(UUID documentId) {
        return documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
    }

    private DocumentView view(Document d) {
        return views(List.of(d)).getFirst();
    }

    private List<DocumentView> views(List<Document> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<DocumentVersion>> byDocument = versions
                .findByDocumentIdInOrderByVersionNumberDesc(list.stream().map(Document::getId).toList())
                .stream().collect(Collectors.groupingBy(DocumentVersion::getDocumentId));
        return list.stream().map(d -> {
            List<DocumentVersion> vs = byDocument.getOrDefault(d.getId(), List.of());
            DocumentVersion latest = vs.isEmpty() ? null : vs.getFirst();
            return new DocumentView(d.getId(), d.getApplicationId(), d.getGrievanceId(), d.getOwnerId(),
                    d.getRequirementCode(), d.getStatus().name(), d.getCurrentVersion(),
                    latest == null ? null : latest.getOriginalFilename(),
                    latest == null ? null : latest.getDetectedMediaType(),
                    latest == null ? 0 : latest.getSizeBytes(),
                    latest == null ? null : latest.getChecksum(),
                    latest == null ? null : latest.getUploadedAt(),
                    d.getRejectionReason(), d.getVerifiedAt(),
                    vs.stream().map(v -> new VersionView(v.getVersionNumber(), v.getOriginalFilename(),
                            v.getDetectedMediaType(), v.getSizeBytes(), v.getUploadedAt())).toList());
        }).toList();
    }

    private void removeOnRollback(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.delete(key);
                    }
                }
            });
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
