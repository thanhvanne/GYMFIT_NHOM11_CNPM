package com.gymfit.membership.dto;

import com.gymfit.branch.ServiceCode;
import com.gymfit.membership.MembershipStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record MembershipResponse(
        Long id,
        Long memberId,
        Long planId,
        Long orderId,
        Long branchId,
        MembershipStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Instant activatedAtUtc,
        Instant endedAtUtc,
        Long replacedByMembershipId,
        Set<ServiceCode> services,
        Instant createdAtUtc
) {
}