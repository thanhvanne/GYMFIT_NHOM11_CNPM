package com.gymfit.checkin.dto;

import com.gymfit.branch.ServiceCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualCheckInRequest(
        Long branchId,

        @NotBlank
        @Size(max = 30)
        String memberIdentifier,

        @NotNull
        ServiceCode serviceCode
) {
}