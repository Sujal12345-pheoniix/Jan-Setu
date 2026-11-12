package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.WorkflowTask;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID>, JpaSpecificationExecutor<WorkflowTask> {

    Optional<WorkflowTask> findFirstByApplicationIdAndStatusInOrderByCreatedAtDesc(UUID applicationId,
                                                                                  Collection<String> statuses);

    List<WorkflowTask> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    List<WorkflowTask> findByApplicationIdInAndStatusIn(Collection<UUID> applicationIds, Collection<String> statuses);
}
