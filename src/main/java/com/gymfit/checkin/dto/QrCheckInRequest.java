package com.gymfit.checkin.dto;

import com.gymfit.branch.ServiceCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QrCheckInRequest(
        Long branchId,

        @NotBlank
        String token,

        @NotNull
        ServiceCode serviceCode
) {
}