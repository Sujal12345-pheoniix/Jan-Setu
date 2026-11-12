package com.jansetu.modules.identity.repository;

import com.jansetu.modules.identity.domain.RefreshSession;
import com.jansetu.modules.identity.domain.RefreshSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    /** Row lock so two concurrent refreshes with the same token cannot both rotate it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(String tokenHash);

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId,
                                                                                    RefreshSessionStatus status,
                                                                                    Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshSession s set s.status = com.jansetu.modules.identity.domain.RefreshSessionStatus.REVOKED, s.revokedAt = :now, s.revokeReason = :reason
            where s.familyId = :familyId and s.status = com.jansetu.modules.identity.domain.RefreshSessionStatus.ACTIVE""")
    int revokeFamily(UUID familyId, String reason, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshSession s set s.status = com.jansetu.modules.identity.domain.RefreshSessionStatus.REVOKED, s.revokedAt = :now, s.revokeReason = :reason
            where s.userId = :userId and s.status = com.jansetu.modules.identity.domain.RefreshSessionStatus.ACTIVE""")
    int revokeAllForUser(UUID userId, String reason, Instant now);
}
