package com.jansetu.modules.catalogue.repository;

import com.jansetu.modules.catalogue.domain.ServiceDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, UUID>,
        JpaSpecificationExecutor<ServiceDefinition> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = "requirements")
    @Query("select s from ServiceDefinition s where s.id = :id")
    Optional<ServiceDefinition> findWithRequirementsById(UUID id);
}
