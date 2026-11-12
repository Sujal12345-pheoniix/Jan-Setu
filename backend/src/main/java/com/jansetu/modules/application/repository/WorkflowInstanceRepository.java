package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.WorkflowInstance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    Optional<WorkflowInstance> findByApplicationIdAndStatus(UUID applicationId, String status);
}
