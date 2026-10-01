package com.gymfit.branch.dto;

import com.gymfit.branch.ServiceCode;

public record BranchServiceResponse(
        Long branchId,
        ServiceCode serviceCode,
        Integer bookingDurationMinutes,
        Integer capacity,
        Boolean bookingEnabled
) {
}