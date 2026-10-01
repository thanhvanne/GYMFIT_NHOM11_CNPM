package com.gymfit.plan.dto;

import com.gymfit.branch.ServiceCode;
import com.gymfit.plan.PlanStatus;
import com.gymfit.plan.PlanTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record PlanResponse(
        Long id,
        Long branchId,
        String planCode,
        String name,
        PlanTier tier,
        Integer durationDays,
        BigDecimal price,
        String description,
        PlanStatus status,
        Set<ServiceCode> services,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}