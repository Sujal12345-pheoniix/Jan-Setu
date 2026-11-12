package com.jansetu.modules.grievance.repository;

import com.jansetu.modules.grievance.domain.GrievanceRecords.GrievanceHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrievanceHistoryRepository extends JpaRepository<GrievanceHistory, UUID> {

    List<GrievanceHistory> findByGrievanceIdOrderByOccurredAtAsc(UUID grievanceId);
}
