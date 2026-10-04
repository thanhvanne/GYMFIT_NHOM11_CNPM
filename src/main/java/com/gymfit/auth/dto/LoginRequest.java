package com.gymfit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Đăng nhập: tên đăng nhập là <b>email</b> hoặc <b>mã hội viên</b> (D1, F4).
 *
 * <p>Không dùng {@code @Email} nữa – nếu thiếu dấu {@code @} thì
 * {@code AuthService.normalizeLogin} sẽ ghép thêm {@code @member.gymfit.local}.
 * Tên field giữ là {@code email} để không phá client đang gửi.
 */
public record LoginRequest(
        @NotBlank(message = "Tài khoản không được để trống")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {
}
