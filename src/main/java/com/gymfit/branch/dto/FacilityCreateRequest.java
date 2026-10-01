package com.gymfit.branch.dto;

import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.FacilityType;
import com.gymfit.branch.ServiceCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FacilityCreateRequest(
        @NotNull
        Long branchId,

        @NotNull
        ServiceCode serviceCode,

        @NotNull
        FacilityType facilityType,

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        @Min(1)
        Integer capacity,

        @NotNull
        FacilityStatus status
) {
}