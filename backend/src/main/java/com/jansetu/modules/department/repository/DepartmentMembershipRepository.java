package com.jansetu.modules.department.repository;

import com.jansetu.modules.department.domain.DepartmentMembership;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepartmentMembershipRepository extends JpaRepository<DepartmentMembership, UUID> {

    @Query("select m.departmentId from DepartmentMembership m where m.userId = :userId")
    List<UUID> findDepartmentIdsByUserId(UUID userId);

    @Query("select distinct m.userId from DepartmentMembership m where m.departmentId in :departmentIds")
    List<UUID> findUserIdsByDepartmentIds(Collection<UUID> departmentIds);

    @Query("select m.userId, m.departmentId from DepartmentMembership m where m.userId in :userIds")
    List<Object[]> findMembershipPairsByUserIds(Collection<UUID> userIds);

    Page<DepartmentMembership> findByDepartmentId(UUID departmentId, Pageable pageable);

    Optional<DepartmentMembership> findByDepartmentIdAndUserId(UUID departmentId, UUID userId);

    boolean existsByDepartmentIdAndUserId(UUID departmentId, UUID userId);
}
