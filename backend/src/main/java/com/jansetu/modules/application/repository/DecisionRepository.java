package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.CaseRecords.Decision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByApplicationIdOrderByDecidedAtAsc(UUID applicationId);

    Optional<Decision> findFirstByApplicationIdAndDecisionOrderByDecidedAtDesc(UUID applicationId, String decision);
}
