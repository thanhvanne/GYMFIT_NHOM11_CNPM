package com.gymfit.order.dto;

import com.gymfit.order.OrderItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull
        OrderItemType itemType,

        Long planId,

        Long productId,

        @NotNull
        @Min(1)
        Integer quantity
) {
}