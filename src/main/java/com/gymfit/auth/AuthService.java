package com.gymfit.auth;

import com.gymfit.audit.AuditService;
import com.gymfit.auth.dto.ChangePasswordRequest;
import com.gymfit.auth.dto.CurrentUserResponse;
import com.gymfit.auth.dto.LoginResponse;
import com.gymfit.common.error.BadRequestException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.JwtService;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.MemberAccountService;
import com.gymfit.member.MemberRepository;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MemberRepository memberRepository;

    public LoginResponse login(String email, String password) {

        String normalizedEmail = resolveLogin(email);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        password
                )
        );

        AppUser user = appUserRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow();

        AppPrincipal principal = new AppPrincipal(user);

        return new LoginResponse(
                jwtService.createToken(principal),
                "Bearer",
                toCurrentUser(principal)
        );
    }

    public CurrentUserResponse currentUser(AppPrincipal principal) {
        return toCurrentUser(principal);
    }

    /**
     * Đổi mật khẩu ({@code POST /api/v1/auth/change-password}).
     *
     * <p>Quy tắc: đúng mật khẩu hiện tại; mới khác cũ; ≥ 8 ký tự, có chữ
     * <b>và</b> số; không trùng tên đăng nhập. Thành công ⇒ tắt cờ
     * {@code mustChangePassword} và ghi audit {@code PASSWORD_CHANGED}
     * (chỉ ghi email – không ghi mật khẩu).
     */
    @Transactional
    public void changePassword(
            AppPrincipal principal,
            ChangePasswordRequest request
    ) {

        AppUser user = appUserRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new NotFoundException(
                        "user_not_found",
                        "Không tìm thấy người dùng"
                ));

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {
            throw new BadRequestException(
                    "invalid_current_password",
                    "Mật khẩu hiện tại không đúng"
            );
        }

        String newPassword = request.newPassword();

        if (passwordEncoder.matches(
                newPassword,
                user.getPasswordHash()
        )) {
            throw new BadRequestException(
                    "password_unchanged",
                    "Mật khẩu mới phải khác mật khẩu hiện tại"
            );
        }

        if (!isStrongPassword(
                newPassword,
                user.getEmail()
        )) {
            throw new BadRequestException(
                    "weak_password",
                    "Mật khẩu mới cần ≥ 8 ký tự, gồm chữ và số"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(newPassword)
        );
        user.setMustChangePassword(false);
        user.setUpdatedAtUtc(TimeUtil.now());

        appUserRepository.save(user);

        auditService.record(
                principal.getUserId(),
                "PASSWORD_CHANGED",
                "APP_USER",
                user.getId(),
                user.getBranchId(),
                Map.of(
                        "email", user.getEmail()
                )
        );
    }

    /**
     * Chuẩn hóa tên đăng nhập (D1, F4):
     * có {@code @} ⇒ email viết thường; không có ⇒ ghép
     * {@code @member.gymfit.local} (đăng nhập bằng mã hội viên).
     */
    static String normalizeLogin(String input) {

        String value = input == null
                ? ""
                : input.trim().toLowerCase(Locale.ROOT);

        return value.contains("@")
                ? value
                : value + "@" + MemberAccountService.LOGIN_DOMAIN;
    }

    /**
     * Quyết định tên đăng nhập sẽ dùng (F4).
     *
     * <p>Người dùng gõ mã hội viên nhưng tài khoản lại đang mang <b>email</b>
     * (hội viên có email khi được cấp tài khoản) ⇒ tra {@code member} theo mã
     * rồi lấy {@code app_user.email} của hội viên đó, để cả hai cách gõ đều
     * đăng nhập được. Không tra thấy thì dùng tên đăng nhập ghép theo
     * {@link #normalizeLogin} (tài khoản sinh từ mã hội viên).
     */
    String resolveLogin(String input) {

        String raw = input == null
                ? ""
                : input.trim().toLowerCase(Locale.ROOT);

        if (raw.isEmpty() || raw.contains("@")) {
            return normalizeLogin(raw);
        }

        String candidate = normalizeLogin(raw);

        boolean candidateExists = appUserRepository
                .findByEmailIgnoreCase(candidate)
                .isPresent();

        if (candidateExists) {
            return candidate;
        }

        return memberRepository
                .findByMemberCodeIgnoreCase(raw)
                .flatMap(member -> appUserRepository
                        .findByMemberId(member.getId()))
                .map(AppUser::getEmail)
                .orElse(candidate);
    }

    private boolean isStrongPassword(
            String password,
            String username
    ) {

        if (password == null || password.length() < 8) {
            return false;
        }

        if (username != null
                && password.equalsIgnoreCase(username)) {
            return false;
        }

        boolean hasLetter = password.chars()
                .anyMatch(Character::isLetter);

        boolean hasDigit = password.chars()
                .anyMatch(Character::isDigit);

        return hasLetter && hasDigit;
    }

    private CurrentUserResponse toCurrentUser(
            AppPrincipal principal
    ) {
        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getRole(),
                principal.getBranchId(),
                principal.getMemberId(),
                principal.isMustChangePassword()
        );
    }
}
