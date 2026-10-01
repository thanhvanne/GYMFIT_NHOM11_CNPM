package com.gymfit.user.dto;

import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        RoleCode role,
        UserStatus status,
        Long branchId,
        Long memberId,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}