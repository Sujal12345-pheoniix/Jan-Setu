package com.jansetu.common.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByActorIdAndOperationAndIdempotencyKey(UUID actorId, String operation, String key);
}
