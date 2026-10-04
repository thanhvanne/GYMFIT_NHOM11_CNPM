package com.gymfit.member.dto;

import com.gymfit.member.MemberStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Hồ sơ hội viên.
 *
 * @param hasAccount      hội viên đã có tài khoản đăng nhập chưa (F5)
 * @param accountUsername tên đăng nhập (email hoặc {@code xxx@member.gymfit.local});
 *                        {@code null} khi chưa có tài khoản
 */
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
        Instant updatedAtUtc,
        boolean hasAccount,
        String accountUsername
) {
}
