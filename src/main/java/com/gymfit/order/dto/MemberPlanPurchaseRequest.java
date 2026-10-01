package com.gymfit.order.dto;

import jakarta.validation.constraints.NotNull;

public record MemberPlanPurchaseRequest(
        @NotNull
        Long planId
) {
}