package com.gymfit.branch.dto;

import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.FacilityType;
import com.gymfit.branch.ServiceCode;

import java.time.Instant;

public record FacilityResponse(
        Long id,
        Long branchId,
        ServiceCode serviceCode,
        FacilityType facilityType,
        String name,
        Integer capacity,
        FacilityStatus status,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}