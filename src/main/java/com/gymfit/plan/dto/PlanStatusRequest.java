package com.gymfit.plan.dto;

import com.gymfit.plan.PlanStatus;
import jakarta.validation.constraints.NotNull;

public record PlanStatusRequest(
        @NotNull PlanStatus status
) {
}