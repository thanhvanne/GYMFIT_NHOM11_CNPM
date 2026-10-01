package com.gymfit.member.dto;

import com.gymfit.member.MemberStatus;

import java.time.Instant;
import java.time.LocalDate;

public record MemberResponse(
        Long id,
        String memberCode,
        String fullName,
        String phone,
        String email,
        Long homeBranchId,
        LocalDate dateOfBirth,
        MemberStatus status,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}