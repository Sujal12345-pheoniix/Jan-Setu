package com.jansetu.modules.grievance.repository;

import com.jansetu.modules.grievance.domain.GrievanceRecords.GrievanceComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrievanceCommentRepository extends JpaRepository<GrievanceComment, UUID> {

    List<GrievanceComment> findByGrievanceIdOrderByCreatedAtAsc(UUID grievanceId);

    List<GrievanceComment> findByGrievanceIdAndVisibilityOrderByCreatedAtAsc(UUID grievanceId, String visibility);
}
