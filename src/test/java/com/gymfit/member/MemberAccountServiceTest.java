package com.gymfit.member;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.BranchRepository;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.member.dto.AccountCredentialsResponse;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import com.gymfit.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nghiệm thu F3 – cấp/đặt lại tài khoản hội viên (Mockito, không đụng DB).
 *
 * <p>Chú ý: {@code UserService} chạy THẬT (băm BCrypt cost 12 thật) với repository
 * mock để khẳng định mật khẩu thô không bao giờ được lưu hay ghi audit.
 */
class MemberAccountServiceTest {

    private AppUserRepository userRepository;
    private MemberRepository memberRepository;
    private AuditService auditService;
    private PasswordEncoder encoder;
    private MemberAccountService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        memberRepository = mock(MemberRepository.class);
        auditService = mock(AuditService.class);

        BranchRepository branchRepository =
                mock(BranchRepository.class);

        encoder = new BCryptPasswordEncoder(12);

        // Lưu AppUser: gán id như JPA IDENTITY
        when(userRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser user =
                            invocation.getArgument(0);

                    if (user.getId() == null) {
                        user.setId(500L);
                    }

                    return user;
                });

        UserService userService = new UserService(
                userRepository,
                branchRepository,
                memberRepository,
                encoder,
                auditService
        );

        service = new MemberAccountService(
                userService,
                userRepository,
                new BranchScopeGuard()
        );
    }

    // ------------------------------------------------------------------
    // Cấp tài khoản
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C1: có email → lưu đúng 1 AppUser MEMBER, băm BCrypt, mật khẩu tạm 10 ký tự")
    void capTaiKhoanCoEmail() {

        Member member = member(
                7L,
                "GF00000007",
                "Member7@Gymfit.Local"
        );

        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));

        AccountCredentialsResponse credentials =
                service.issue(admin(), member);

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(userRepository, times(1))
                .save(captor.capture());

        AppUser saved = captor.getValue();

        assertEquals(
                RoleCode.MEMBER,
                saved.getRoleCode()
        );

        assertEquals(
                Long.valueOf(7L),
                saved.getMemberId()
        );

        // Tên đăng nhập = email viết thường
        assertEquals(
                "member7@gymfit.local",
                saved.getEmail()
        );

        assertTrue(saved.isMustChangePassword());
        assertTrue(saved.getStatus() == UserStatus.ACTIVE);
        assertEquals(
                saved.getEmail(),
                credentials.username()
        );

        // Mật khẩu thô KHÔNG được lưu – chỉ có hash, và hash khớp lại được
        assertEquals(
                10,
                credentials.temporaryPassword().length()
        );

        assertNotEquals(
                credentials.temporaryPassword(),
                saved.getPasswordHash()
        );

        assertTrue(
                encoder.matches(
                        credentials.temporaryPassword(),
                        saved.getPasswordHash()
                ),
                "hash khong khop mat khau tam"
        );

        assertTrue(credentials.mustChangePassword());
        assertEquals(Long.valueOf(500L), credentials.userId());
    }

    @Test
    @DisplayName("C2: không có email → username dạng gf........@member.gymfit.local")
    void capTaiKhoanKhongCoEmail() {

        Member member = member(
                8L,
                "GF00000008",
                null
        );

        when(memberRepository.findById(8L))
                .thenReturn(Optional.of(member));

        AccountCredentialsResponse credentials =
                service.issue(admin(), member);

        assertEquals(
                "gf00000008@member.gymfit.local",
                credentials.username()
        );

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(userRepository, times(1))
                .save(captor.capture());

        assertEquals(
                "gf00000008@member.gymfit.local",
                captor.getValue().getEmail()
        );
    }

    @Test
    @DisplayName("C6: hội viên đã có tài khoản → member_account_exists, không lưu gì")
    void issueHoiVienDaCoTaiKhoan() {

        Member member = member(
                9L,
                "GF00000009",
                "member9@gymfit.local"
        );

        when(userRepository.findByMemberId(9L))
                .thenReturn(Optional.of(
                        AppUser.builder()
                                .id(55L)
                                .email("member9@gymfit.local")
                                .build()
                ));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.issue(admin(), member)
        );

        assertEquals(
                "member_account_exists",
                exception.getCode()
        );

        verify(userRepository, never())
                .save(any(AppUser.class));
    }

    // ------------------------------------------------------------------
    // Đặt lại mật khẩu
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C8a: đặt lại mật khẩu khi hội viên chưa có tài khoản → account_not_found")
    void resetPasswordKhongCoTaiKhoan() {

        Member member = member(
                11L,
                "GF00000011",
                "member11@gymfit.local"
        );

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> service.resetPassword(admin(), member)
        );

        assertEquals(
                "account_not_found",
                exception.getCode()
        );

        verify(userRepository, never())
                .save(any(AppUser.class));
    }

    @Test
    @DisplayName("C8b: đặt lại mật khẩu thành công → hash đổi, bật cờ bắt buộc đổi mật khẩu")
    void resetPasswordThanhCong() {

        Member member = member(
                12L,
                "GF00000012",
                "member12@gymfit.local"
        );

        String oldHash =
                encoder.encode("MatKhauCu1234");

        AppUser existing = AppUser.builder()
                .id(66L)
                .email("member12@gymfit.local")
                .passwordHash(oldHash)
                .mustChangePassword(false)
                .build();

        when(userRepository.findByMemberId(12L))
                .thenReturn(Optional.of(existing));

        AccountCredentialsResponse credentials =
                service.resetPassword(admin(), member);

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(userRepository, times(1))
                .save(captor.capture());

        AppUser saved = captor.getValue();

        assertNotEquals(
                oldHash,
                saved.getPasswordHash()
        );

        assertTrue(
                encoder.matches(
                        credentials.temporaryPassword(),
                        saved.getPasswordHash()
                )
        );

        assertTrue(saved.isMustChangePassword());
        assertFalse(
                encoder.matches(
                        "MatKhauCu1234",
                        saved.getPasswordHash()
                ),
                "mat khau cu van khop -> khong doi"
        );
    }

    // ------------------------------------------------------------------
    // C7: audit không rò mật khẩu
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C7: audit chỉ ghi định danh – không giá trị nào chứa mật khẩu thô")
    void auditKhongRoiMatKhau() {

        Member withEmail = member(
                7L,
                "GF00000007",
                "Member7@Gymfit.Local"
        );

        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(withEmail));

        AccountCredentialsResponse issued =
                service.issue(admin(), withEmail);

        Member toReset = member(
                10L,
                "GF00000010",
                "member10@gymfit.local"
        );

        when(userRepository.findByMemberId(10L))
                .thenReturn(Optional.of(
                        AppUser.builder()
                                .id(70L)
                                .email("member10@gymfit.local")
                                .passwordHash(
                                        encoder.encode("Cu123456")
                                )
                                .build()
                ));

        AccountCredentialsResponse reset =
                service.resetPassword(admin(), toReset);

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

        assertTrue(actions.getAllValues()
                .contains("USER_CREATED"));

        assertTrue(actions.getAllValues()
                .contains("PASSWORD_RESET"));

        for (Object value : details.getAllValues()
                .toArray()) {

            @SuppressWarnings("unchecked")
            Map<String, ?> map = (Map<String, ?>) value;

            for (Map.Entry<String, ?> entry
                    : map.entrySet()) {

                String text =
                        String.valueOf(entry.getValue());

                assertFalse(
                        text.contains(
                                issued.temporaryPassword()
                        ),
                        "mat khau tam lon bi ghi audit: "
                                + entry.getKey()
                );

                assertFalse(
                        text.contains(
                                reset.temporaryPassword()
                        ),
                        "mat khau tam 2 bi ghi audit: "
                                + entry.getKey()
                );
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Member member(
            Long id,
            String memberCode,
            String email
    ) {
        return Member.builder()
                .id(id)
                .memberCode(memberCode)
                .fullName("Nguyen Van A")
                .phone("09000000" + id)
                .email(email)
                .homeBranchId(1L)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private AppPrincipal admin() {
        return principal(RoleCode.ADMIN, null);
    }

    private AppPrincipal principal(
            RoleCode role,
            Long branchId
    ) {
        return new AppPrincipal(
                AppUser.builder()
                        .id(100L)
                        .fullName("Quan tri vien")
                        .email("admin@gymfit.local")
                        .passwordHash("hash")
                        .roleCode(role)
                        .status(UserStatus.ACTIVE)
                        .branchId(branchId)
                        .build()
        );
    }
}
