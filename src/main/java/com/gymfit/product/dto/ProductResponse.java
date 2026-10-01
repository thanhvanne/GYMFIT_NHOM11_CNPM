package com.gymfit.product.dto;

import com.gymfit.product.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String category,
        BigDecimal price,
        ProductStatus status,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}