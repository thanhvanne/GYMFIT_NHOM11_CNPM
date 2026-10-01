package com.gymfit.booking.dto;

import com.gymfit.branch.ServiceCode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookingCreateRequest(
        Long memberId,

        @NotNull
        Long branchId,

        @NotNull
        ServiceCode serviceCode,

        @NotNull
        Long facilityId,

        @NotNull
        Instant startsAt
) {
}