package com.gymfit.inventory.dto;

import java.time.Instant;

public record InventoryResponse(
        Long id,
        Long branchId,
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        Instant updatedAtUtc
) {
}