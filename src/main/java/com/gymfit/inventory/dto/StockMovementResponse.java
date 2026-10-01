package com.gymfit.inventory.dto;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long branchId,
        Long productId,
        Integer quantityDelta,
        String reason,
        Long performedByUserId,
        Instant createdAtUtc
) {
}