package com.gymfit.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderCreateRequest(
        @NotNull
        Long branchId,

        Long memberId,

        @NotEmpty
        List<@Valid OrderItemRequest> items
) {
}