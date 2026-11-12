package com.jansetu.modules.document.repository;

import com.jansetu.modules.document.domain.DocumentVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(UUID documentId, int versionNumber);

    List<DocumentVersion> findByDocumentIdInOrderByVersionNumberDesc(Collection<UUID> documentIds);
}
