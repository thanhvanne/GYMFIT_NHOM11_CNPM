package com.gymfit.auth;

import com.gymfit.audit.AuditService;
import com.gymfit.common.security.JwtService;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F4 – đăng nhập bằng mã hội viên (D1).
 */
class LoginIdentifierTest {

    private AppUserRepository appUserRepository;
    private MemberRepository memberRepository;
    private AuthService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        memberRepository = mock(MemberRepository.class);

        service = new AuthService(
                mock(AuthenticationManager.class),
                appUserRepository,
                mock(JwtService.class),
                mock(PasswordEncoder.class),
                mock(AuditService.class),
                memberRepository
        );
    }

    @Test
    @DisplayName("Mã hội viên không có @ → ghép @member.gymfit.local")
    void maHoiVien() {
        assertEquals(
                "gf00000007@member.gymfit.local",
                AuthService.normalizeLogin("GF00000007")
        );

        assertEquals(
                "gf00000007@member.gymfit.local",
                AuthService.normalizeLogin("  GF00000007  ")
        );
    }

    @Test
    @DisplayName("Email → trim + viết thường, giữ nguyên phần sau @")
    void email() {
        assertEquals(
                "a@b.com",
                AuthService.normalizeLogin("A@B.com ")
        );

        assertEquals(
                "admin@gymfit.local",
                AuthService.normalizeLogin(" Admin@Gymfit.Local ")
        );

        // Email không cần đụng tới repository
        assertEquals(
                "a@b.com",
                service.resolveLogin("A@B.com ")
        );

        verify(memberRepository, never())
                .findByMemberCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Mã hội viên chưa có tài khoản theo mã → dùng tên ghép")
    void maChuaCoTaiKhoan() {
        when(appUserRepository.findByEmailIgnoreCase(
                "gf00000007@member.gymfit.local"))
                .thenReturn(Optional.empty());

        when(memberRepository.findByMemberCodeIgnoreCase(
                "gf00000007"))
                .thenReturn(Optional.empty());

        assertEquals(
                "gf00000007@member.gymfit.local",
                service.resolveLogin("GF00000007")
        );
    }

    @Test
    @DisplayName("Tài khoản đang dùng email → gõ mã hội viên vẫn ra email đó")
    void maHoiVienTraVeEmailTaiKhoan() {

        when(appUserRepository.findByEmailIgnoreCase(
                "gf000001@member.gymfit.local"))
                .thenReturn(Optional.empty());

        when(memberRepository.findByMemberCodeIgnoreCase(
                "gf000001"))
                .thenReturn(Optional.of(
                        Member.builder()
                                .id(1L)
                                .memberCode("GF000001")
                                .build()
                ));

        when(appUserRepository.findByMemberId(1L))
                .thenReturn(Optional.of(
                        AppUser.builder()
                                .id(1L)
                                .email("member1@gymfit.local")
                                .build()
                ));

        assertEquals(
                "member1@gymfit.local",
                service.resolveLogin("GF000001")
        );
    }

    @Test
    @DisplayName("Tài khoản sinh đúng theo mã hội viên → dùng thẳng, không tra member")
    void maHoiVienCoTaiKhoanTheoMa() {

        when(appUserRepository.findByEmailIgnoreCase(
                "gf00000009@member.gymfit.local"))
                .thenReturn(Optional.of(
                        AppUser.builder()
                                .id(9L)
                                .email("gf00000009@member.gymfit.local")
                                .build()
                ));

        assertEquals(
                "gf00000009@member.gymfit.local",
                service.resolveLogin("GF00000009")
        );

        verify(memberRepository, never())
                .findByMemberCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }
}
