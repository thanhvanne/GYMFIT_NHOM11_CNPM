package com.gymfit.member.dto;

/**
 * Kết quả {@code POST /api/v1/members}.
 *
 * @param member  hồ sơ hội viên vừa tạo (luôn có)
 * @param account thông tin đăng nhập nếu {@code createAccount=true},
 *                ngược lại {@code null}
 */
public record MemberCreateResponse(
        MemberResponse member,
        AccountCredentialsResponse account
) {
}
