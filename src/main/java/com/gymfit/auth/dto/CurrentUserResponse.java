package com.gymfit.auth.dto;

import com.gymfit.user.RoleCode;

public record CurrentUserResponse(
        Long userId,
        String fullName,
        String email,
        RoleCode role,
        Long branchId,
        Long memberId
) {
}