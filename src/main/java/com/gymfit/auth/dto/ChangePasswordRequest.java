package com.gymfit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đổi mật khẩu ({@code POST /api/v1/auth/change-password}).
 *
 * <p>Quy tắc nghiệp vụ (kiểm ở service): mật khẩu hiện tại đúng, mới khác cũ,
 * ≥ 8 ký tự và phải có cả chữ lẫn số.
 */
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 72)
        String newPassword
) {
}
