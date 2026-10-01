package com.gymfit.audit.dto;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
        Long actorUserId,
        String action,
        String entityType,
        Long entityId,
        Long branchId,
        String detailsJson,
        Instant createdAtUtc
) {
}