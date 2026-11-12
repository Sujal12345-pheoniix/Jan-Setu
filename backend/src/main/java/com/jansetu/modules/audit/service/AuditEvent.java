package com.jansetu.modules.audit.service;

import com.jansetu.modules.audit.domain.AuditOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An audit event to record. Details must contain only minimal, non-sensitive metadata: never passwords, tokens,
 * secrets or document contents.
 */
public record AuditEvent(String action, String resourceType, String resourceId, AuditOutcome outcome, UUID actorId,
                         Map<String, Object> details) {

    public static AuditEvent success(String action, String resourceType, Object resourceId) {
        return new AuditEvent(action, resourceType, resourceId == null ? null : resourceId.toString(),
                AuditOutcome.SUCCESS, null, Map.of());
    }

    public static AuditEvent failure(String action, String resourceType, Object resourceId) {
        return new AuditEvent(action, resourceType, resourceId == null ? null : resourceId.toString(),
                AuditOutcome.FAILURE, null, Map.of());
    }

    public static AuditEvent denied(String action, String resourceType, Object resourceId) {
        return new AuditEvent(action, resourceType, resourceId == null ? null : resourceId.toString(),
                AuditOutcome.DENIED, null, Map.of());
    }

    /** Explicit actor, for events where the security context is not (yet) populated, e.g. login. */
    public AuditEvent actor(UUID actor) {
        return new AuditEvent(action, resourceType, resourceId, outcome, actor, details);
    }

    public AuditEvent detail(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(details);
        copy.put(key, value);
        return new AuditEvent(action, resourceType, resourceId, outcome, actorId, copy);
    }
}
