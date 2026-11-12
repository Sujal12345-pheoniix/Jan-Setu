package com.jansetu.modules.audit.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.modules.audit.domain.AuditLog;
import com.jansetu.modules.audit.domain.AuditOutcome;
import com.jansetu.modules.audit.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Read-only audit trail (AUDIT:READ)")
public class AuditLogController {

    public record AuditLogResponse(UUID id, Instant occurredAt, UUID actorId, String actorType, String action,
                                   String resourceType, String resourceId, String outcome, String requestId,
                                   String ipAddress, Map<String, Object> details) {

        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getActorId(), log.getActorType().name(),
                    log.getAction(), log.getResourceType(), log.getResourceId(), log.getOutcome().name(),
                    log.getRequestId(), log.getIpAddress(), log.getDetails());
        }
    }

    private final AuditQueryService queryService;

    public AuditLogController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT:READ')")
    @Operation(summary = "Search audit records", description = "Sortable by occurredAt, action. Viewing is itself audited.")
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject PageQuery page) {
        var filter = new AuditQueryService.AuditFilter(actorId, action, resourceType, resourceId, outcome, from, to);
        return ApiResponse.of(PageResponse.from(queryService.search(filter, page), AuditLogResponse::from));
    }
}
