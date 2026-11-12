package com.jansetu.modules.catalogue.repository;

import com.jansetu.modules.catalogue.domain.ServiceVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ServiceVersionRepository extends JpaRepository<ServiceVersion, UUID>, JpaSpecificationExecutor<ServiceVersion> {

    Optional<ServiceVersion> findByServiceIdAndVersionNumber(UUID serviceId, int versionNumber);
}
