package com.gymfit.product.dto;

import com.gymfit.product.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank
        @Size(max = 40)
        String sku,

        @NotBlank
        @Size(max = 140)
        String name,

        @NotBlank
        @Size(max = 60)
        String category,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal price,

        @NotNull
        ProductStatus status
) {
}