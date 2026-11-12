package com.jansetu.modules.grievance.repository;

import com.jansetu.modules.grievance.domain.GrievanceRecords.GrievanceFeedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrievanceFeedbackRepository extends JpaRepository<GrievanceFeedback, UUID> {

    Optional<GrievanceFeedback> findByGrievanceId(UUID grievanceId);
}
