package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.CaseRecords.StatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

    List<StatusHistory> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);

    List<StatusHistory> findByApplicationIdAndCitizenVisibleTrueOrderByOccurredAtAsc(UUID applicationId);
}
