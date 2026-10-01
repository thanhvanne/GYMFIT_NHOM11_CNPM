package com.gymfit.branch.dto;

import com.gymfit.branch.ServiceCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BranchServiceItemRequest(
        @NotNull
        ServiceCode serviceCode,

        @NotNull
        @Min(30)
        @Max(120)
        Integer bookingDurationMinutes,

        @NotNull
        @Min(1)
        Integer capacity,

        @NotNull
        Boolean bookingEnabled
) {
}