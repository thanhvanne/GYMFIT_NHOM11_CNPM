package com.gymfit.plan.dto;

import com.gymfit.branch.ServiceCode;
import com.gymfit.plan.PlanTier;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Set;

public record PlanCreateRequest(
        @NotNull
        Long branchId,

        @NotBlank
        @Size(max = 40)
        String planCode,

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        PlanTier tier,

        @NotNull
        @Min(1)
        Integer durationDays,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal price,

        @Size(max = 500)
        String description,

        @NotEmpty
        Set<ServiceCode> services
) {
}