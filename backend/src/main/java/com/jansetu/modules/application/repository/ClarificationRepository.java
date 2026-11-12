package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.ClarificationRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClarificationRepository extends JpaRepository<ClarificationRequest, UUID> {

    List<ClarificationRequest> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    Optional<ClarificationRequest> findFirstByApplicationIdAndStatus(UUID applicationId, String status);
}
