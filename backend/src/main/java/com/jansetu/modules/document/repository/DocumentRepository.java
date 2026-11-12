package com.jansetu.modules.document.repository;

import com.jansetu.modules.document.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByApplicationIdOrderByRequirementCodeAsc(UUID applicationId);

    List<Document> findByGrievanceIdOrderByCreatedAtAsc(UUID grievanceId);

    Optional<Document> findByApplicationIdAndRequirementCode(UUID applicationId, String requirementCode);
}
