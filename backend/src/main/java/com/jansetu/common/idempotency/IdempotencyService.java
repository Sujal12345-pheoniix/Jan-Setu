package com.jansetu.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jansetu.common.crypto.SecureTokens;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Protects create commands against duplicate client retries using the {@code Idempotency-Key} header.
 *
 * <p>Must run inside the caller's business transaction: the record is inserted first (the unique constraint
 * serialises concurrent duplicates), the action runs, and the response is stored. If the action fails the whole
 * transaction, including the record, rolls back so the client can retry.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Result of an idempotent execution; {@code replayed} is true when a stored response was returned. */
    public record Result<T>(T value, boolean replayed) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Result<T> execute(UUID actorId, String operation, String key, Object request, Class<T> responseType,
                                 Function<T, Object> resourceIdOf, Supplier<T> action) {
        if (key == null || key.isBlank() || actorId == null) {
            return new Result<>(action.get(), false);
        }
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER,
                    HEADER + " must be 8-100 characters of letters, digits, '-' or '_'.");
        }
        String requestHash = SecureTokens.sha256(toJson(request));
        Instant now = clock.instant();

        Optional<IdempotencyRecord> existing = repository.findByActorIdAndOperationAndIdempotencyKey(actorId, operation, key);
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(now)) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This Idempotency-Key was already used with a different request body.");
            }
            if (record.getResponseBody() == null) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                        "A request with this Idempotency-Key is still being processed.");
            }
            return new Result<>(fromJson(record.getResponseBody(), responseType), true);
        }
        existing.ifPresent(expired -> {
            repository.delete(expired);
            repository.flush();
        });

        IdempotencyRecord record = new IdempotencyRecord(actorId, operation, key, requestHash, now, now.plus(RETENTION));
        try {
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                    "A request with this Idempotency-Key is already being processed.");
        }
        T result = action.get();
        Object resourceId = resourceIdOf.apply(result);
        record.complete(201, toJson(result), resourceId == null ? null : resourceId.toString());
        return new Result<>(result, false);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise idempotent payload", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialise stored idempotent response", e);
        }
    }
}
