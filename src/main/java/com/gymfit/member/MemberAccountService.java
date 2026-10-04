package com.gymfit.member;

import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Tài khoản đăng nhập cho hội viên (D1, D2, D4).
 *
 * <p>F2: chỉ có quy tắc <b>tên đăng nhập</b> (hằng {@link #LOGIN_DOMAIN} sẽ được
 * {@code AuthService} dùng lại để cho phép đăng nhập bằng mã hội viên).
 * Nghiệp vụ cấp/đặt lại tài khoản bổ sung ở F3 (khối {@code assertUsernameAvailable},
 * {@code issue}, {@code resetPassword}).
 */
@Service
public class MemberAccountService {

    /**
     * Tên miền đặt cho tài khoản của hội viên không có email
     * (chỉ là định danh, không phải hộp thư thật).
     */
    public static final String LOGIN_DOMAIN =
            "member.gymfit.local";

    /**
     * Quy tắc tên đăng nhập (D1):
     * <ul>
     *     <li>có email → email viết thường (trim + lower);</li>
     *     <li>không có email → {@code <mã hội viên>@member.gymfit.local}.</li>
     * </ul>
     *
     * @param memberEmail email của hội viên (có thể null/rỗng)
     * @param memberCode  mã hội viên, ví dụ {@code GF00000007}
     */
    public static String resolveUsername(
            String memberEmail,
            String memberCode
    ) {

        if (memberEmail != null
                && !memberEmail.isBlank()) {

            return memberEmail
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }

        String code = memberCode == null
                ? ""
                : memberCode.trim();

        return code.toLowerCase(Locale.ROOT)
                + "@" + LOGIN_DOMAIN;
    }
}
