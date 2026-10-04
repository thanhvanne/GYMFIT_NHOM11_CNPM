package com.gymfit.member.dto;

/**
 * Thông tin đăng nhập trả về MỘT lần cho nhân viên (D2, D5).
 *
 * <p>{@code temporaryPassword} là mật khẩu thô – chỉ nằm trong RAM và trong
 * response của các endpoint cấp/đặt lại mật khẩu; tuyệt đối không ghi log,
 * audit hay lưu vào DB dạng rõ.
 */
public record AccountCredentialsResponse(
        Long userId,
        String username,
        String temporaryPassword,
        boolean mustChangePassword
) {
}
