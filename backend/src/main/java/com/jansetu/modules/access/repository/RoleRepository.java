package com.jansetu.modules.access.repository;

import com.jansetu.modules.access.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from Role r where r.id = :id")
    Optional<Role> findWithPermissionsById(UUID id);

    @Override
    Page<Role> findAll(Pageable pageable);
}
