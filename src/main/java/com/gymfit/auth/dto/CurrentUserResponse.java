package com.gymfit.auth.dto;

import com.gymfit.user.RoleCode;

/**
 * Thông tin người dùng đang đăng nhập.
 *
 * @param mustChangePassword {@code true} ⇒ hệ thống chỉ cho gọi
 *                           {@code /api/v1/auth/me} và
 *                           {@code /api/v1/auth/change-password} (D3, F4)
 */
public record CurrentUserResponse(
        Long userId,
        String fullName,
        String email,
        RoleCode role,
        Long branchId,
        Long memberId,
        boolean mustChangePassword
) {
}
