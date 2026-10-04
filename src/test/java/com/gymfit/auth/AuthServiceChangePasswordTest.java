package com.gymfit.auth;

import com.gymfit.audit.AuditService;
import com.gymfit.auth.dto.ChangePasswordRequest;
import com.gymfit.common.error.BadRequestException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.JwtService;
import com.gymfit.member.MemberRepository;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F4 – đổi mật khẩu ({@code POST /api/v1/auth/change-password}).
 */
class AuthServiceChangePasswordTest {

    private static final String OLD_PASSWORD = "MatKhauCu1234";

    private AppUserRepository appUserRepository;
    private AuditService auditService;
    private PasswordEncoder encoder;
    private AuthService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        auditService = mock(AuditService.class);
        encoder = new BCryptPasswordEncoder(12);

        service = new AuthService(
                mock(AuthenticationManager.class),
                appUserRepository,
                mock(JwtService.class),
                encoder,
                auditService,
                mock(MemberRepository.class)
        );

        user = AppUser.builder()
                .id(7L)
                .fullName("Hoi vien mot")
                .email("member1@gymfit.local")
                .passwordHash(encoder.encode(OLD_PASSWORD))
                .roleCode(RoleCode.MEMBER)
                .status(UserStatus.ACTIVE)
                .memberId(1L)
                .mustChangePassword(true)
                .build();

        when(appUserRepository.findById(7L))
                .thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Sai mật khẩu hiện tại → invalid_current_password")
    void saiMatKhauHienTai() {

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.changePassword(
                        principal(),
                        request("SaiKhac1234", "MoiMatKhau9")
                )
        );

        assertEquals(
                "invalid_current_password",
                exception.getCode()
        );
    }

    @Test
    @DisplayName("Mật khẩu mới trùng mật khẩu cũ → password_unchanged")
    void trungMatKhauCu() {

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.changePassword(
                        principal(),
                        request(OLD_PASSWORD, OLD_PASSWORD)
                )
        );

        assertEquals(
                "password_unchanged",
                exception.getCode()
        );
    }

    @Test
    @DisplayName("Mật khẩu mới yếu → weak_password")
    void matKhauYeu() {

        assertWeak("abcdefgh");
        assertWeak("12345678");
        assertWeak("member1@gymfit.local"); // trùng tên đăng nhập
    }

    @Test
    @DisplayName("Đổi thành công → hash mới, tắt cờ bắt buộc đổi, audit không rò mật khẩu")
    void doiThanhCong() {

        String newPassword = "MoiMatKhau9";

        service.changePassword(
                principal(),
                request(OLD_PASSWORD, newPassword)
        );

        ArgumentCaptor<AppUser> userCaptor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(appUserRepository).save(userCaptor.capture());

        AppUser saved = userCaptor.getValue();

        assertTrue(
                encoder.matches(newPassword, saved.getPasswordHash())
        );

        assertFalse(
                encoder.matches(OLD_PASSWORD, saved.getPasswordHash()),
                "mat khau cu van khop -> khong doi"
        );

        // D3: hết nợ đổi mật khẩu
        assertFalse(saved.isMustChangePassword());

        // Audit không chứa mật khẩu
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map> details =
                ArgumentCaptor.forClass(Map.class);

        ArgumentCaptor<String> actions =
                ArgumentCaptor.forClass(String.class);

        verify(auditService, atLeastOnce())
                .record(
                        any(),
                        actions.capture(),
                        any(),
                        any(),
                        any(),
                        details.capture()
                );

        assertTrue(
                actions.getAllValues()
                        .contains("PASSWORD_CHANGED")
        );

        for (Object value : details.getAllValues().toArray()) {

            @SuppressWarnings("unchecked")
            Map<String, ?> map = (Map<String, ?>) value;

            for (Map.Entry<String, ?> entry : map.entrySet()) {

                String text =
                        String.valueOf(entry.getValue());

                assertFalse(
                        text.contains(newPassword),
                        "mat khau moi bi ghi audit: " + entry.getKey()
                );

                assertFalse(
                        text.contains(OLD_PASSWORD),
                        "mat khau cu bi ghi audit: " + entry.getKey()
                );
            }
        }
    }

    // ------------------------------------------------------------------

    private void assertWeak(String newPassword) {

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.changePassword(
                        principal(),
                        request(OLD_PASSWORD, newPassword)
                )
        );

        assertEquals(
                "weak_password",
                exception.getCode(),
                "newPassword=" + newPassword
        );
    }

    private ChangePasswordRequest request(
            String currentPassword,
            String newPassword
    ) {
        return new ChangePasswordRequest(
                currentPassword,
                newPassword
        );
    }

    private AppPrincipal principal() {
        return new AppPrincipal(user);
    }
}
