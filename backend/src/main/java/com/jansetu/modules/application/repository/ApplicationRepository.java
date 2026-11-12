package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.Application;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByReferenceNumber(String referenceNumber);

    /** Collision-safe reference allocation (BUSINESS_LOGIC.md §4). */
    @Query(value = "select nextval('application_reference_seq')", nativeQuery = true)
    long nextReferenceValue();
}
