package com.gymfit.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAdjustRequest(
        @NotNull
        Long branchId,

        @NotNull
        Long productId,

        @NotNull
        Integer quantityDelta,

        @NotBlank
        @Size(max = 200)
        String reason
) {
}