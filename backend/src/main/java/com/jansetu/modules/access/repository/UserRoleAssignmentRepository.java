package com.jansetu.modules.access.repository;

import com.jansetu.modules.access.domain.UserRoleAssignment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignment.Key> {

    @Query("select r.code from UserRoleAssignment a join Role r on r.id = a.roleId where a.userId = :userId")
    List<String> findRoleCodesByUserId(UUID userId);

    @Query("select a.userId, r.code from UserRoleAssignment a join Role r on r.id = a.roleId where a.userId in :userIds")
    List<Object[]> findRoleCodesByUserIds(Collection<UUID> userIds);

    @Query("""
            select distinct p.code from UserRoleAssignment a
            join Role r on r.id = a.roleId
            join r.permissions p
            where a.userId = :userId""")
    List<String> findPermissionCodesByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserRoleAssignment a where a.userId = :userId")
    void deleteByUserId(UUID userId);

    long countByRoleId(UUID roleId);
}
