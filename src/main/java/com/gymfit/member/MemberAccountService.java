package com.gymfit.member;

import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.PasswordGenerator;
import com.gymfit.member.dto.AccountCredentialsResponse;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Tài khoản đăng nhập cho hội viên (D1, D2, D4).
 *
 * <p>Quy tắc tên đăng nhập: email của hội viên viết thường; thiếu email thì
 * {@code <mã hội viên>@member.gymfit.local}. Mật khẩu do {@link PasswordGenerator}
 * sinh ngẫu nhiên, chỉ hiển thị MỘT lần cho nhân viên.
 *
 * <p>Cấp/đặt lại đều bắt buộc qua {@link #requireBranchScope} để BRANCH_MANAGER
 * không thể thao tác ngoài chi nhánh của mình.
 */
@Service
@RequiredArgsConstructor
public class MemberAccountService {

    /**
     * Tên miền đặt cho tài khoản của hội viên không có email
     * (chỉ là định danh, không phải hộp thư thật).
     */
    public static final String LOGIN_DOMAIN =
            "member.gymfit.local";

    private final UserService userService;
    private final AppUserRepository userRepository;
    private final BranchScopeGuard branchScopeGuard;

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

    /**
     * Tính tên đăng nhập và kiểm trùng với {@code app_user} TRƯỚC khi lưu hội viên.
     *
     * @return tên đăng nhập sẽ dùng
     * @throws ConflictException {@code user_email_exists} nếu email đã thuộc
     *                            tài khoản khác
     */
    public String assertUsernameAvailable(
            String memberEmail,
            String memberCode
    ) {

        String username = resolveUsername(
                memberEmail,
                memberCode
        );

        userRepository
                .findByEmailIgnoreCase(username)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "user_email_exists",
                            "Email tài khoản đã được sử dụng"
                    );
                });

        return username;
    }

    /**
     * Cấp tài khoản cho hội viên đã lưu: sinh mật khẩu tạm, băm, lưu
     * {@code app_user} (role MEMBER, {@code mustChangePassword=true}).
     *
     * @return thông tin đăng hiển trả về MỘT lần
     */
    public AccountCredentialsResponse issue(
            AppPrincipal actor,
            Member member
    ) {

        requireBranchScope(
                actor,
                member.getHomeBranchId()
        );

        // Hội viên đã có tài khoản ⇒ dừng ngay (trước khi sinh mật khẩu).
        userRepository
                .findByMemberId(member.getId())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "member_account_exists",
                            "Hội viên đã có tài khoản đăng nhập"
                    );
                });

        String username = resolveUsername(
                member.getEmail(),
                member.getMemberCode()
        );

        String rawPassword =
                PasswordGenerator.generate();

        AppUser saved = userService.createForMember(
                actor.getUserId(),
                member,
                username,
                rawPassword
        );

        return new AccountCredentialsResponse(
                saved.getId(),
                saved.getEmail(),
                rawPassword,
                saved.isMustChangePassword()
        );
    }

    /**
     * Đặt lại mật khẩu cho hội viên đã có tài khoản.
     *
     * @throws NotFoundException {@code account_not_found} nếu hội viên chưa có
     *                            tài khoản đăng nhập
     */
    public AccountCredentialsResponse resetPassword(
            AppPrincipal actor,
            Member member
    ) {

        AppUser user = userRepository
                .findByMemberId(member.getId())
                .orElseThrow(() -> new NotFoundException(
                        "account_not_found",
                        "Hội viên chưa có tài khoản đăng nhập"
                ));

        requireBranchScope(
                actor,
                member.getHomeBranchId()
        );

        String rawPassword =
                PasswordGenerator.generate();

        userService.resetPassword(
                actor.getUserId(),
                user,
                rawPassword,
                member.getHomeBranchId()
        );

        return new AccountCredentialsResponse(
                user.getId(),
                user.getEmail(),
                rawPassword,
                true
        );
    }

    /**
     * D4: ADMIN mọi chi nhánh; BRANCH_MANAGER chỉ {@code home_branch} của mình.
     */
    private void requireBranchScope(
            AppPrincipal actor,
            Long homeBranchId
    ) {

        branchScopeGuard.requireBranch(
                actor,
                homeBranchId
        );
    }
}
