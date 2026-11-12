package com.jansetu.modules.grievance.repository;

import com.jansetu.modules.grievance.domain.Grievance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GrievanceRepository extends JpaRepository<Grievance, UUID>, JpaSpecificationExecutor<Grievance> {

    @Query(value = "select nextval('grievance_reference_seq')", nativeQuery = true)
    long nextReferenceValue();
}
