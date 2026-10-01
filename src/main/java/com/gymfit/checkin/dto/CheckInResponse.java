package com.gymfit.checkin.dto;

import com.gymfit.branch.ServiceCode;
import com.gymfit.checkin.CheckInMethod;
import com.gymfit.checkin.CheckInResult;

import java.time.Instant;

public record CheckInResponse(
        Long id,
        Long memberId,
        Long membershipId,
        Long branchId,
        ServiceCode serviceCode,
        CheckInMethod method,
        CheckInResult result,
        String reason,
        Long performedByUserId,
        Instant createdAtUtc
) {
}