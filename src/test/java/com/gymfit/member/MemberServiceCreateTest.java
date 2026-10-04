package com.gymfit.member;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.member.dto.MemberCreateRequest;
import com.gymfit.member.dto.MemberCreateResponse;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import com.gymfit.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nghiệm thu F3 – luồng {@code MemberService.create} (Mockito, không DB).
 *
 * <p>Chain thật: {@code MemberService → MemberAccountService → UserService}
 * với repository mock, nên các khẳng định về AppUser/BCrypt là thật.
 */
class MemberServiceCreateTest {

    private final Member[] savedHolder = new Member[1];

    private MemberRepository memberRepository;
    private AppUserRepository userRepository;
    private AuditService auditService;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        userRepository = mock(AppUserRepository.class);
        auditService = mock(AuditService.class);

        BranchRepository branchRepository =
                mock(BranchRepository.class);

        when(branchRepository.findById(1L))
                .thenReturn(Optional.of(
                        Branch.builder()
                                .id(1L)
                                .code("HN1")
                                .name("Chi nhanh Ha Noi")
                                .status(BranchStatus.ACTIVE)
                                .build()
                ));

        // Lưu hội viên → gán id như JPA IDENTITY
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    member.setId(77L);
                    savedHolder[0] = member;
                    return member;
                });

        when(memberRepository.findById(77L))
                .thenAnswer(invocation ->
                        Optional.ofNullable(savedHolder[0]));

        // Lưu AppUser trả về chính đối tượng (như JPA)
        when(userRepository.save(any(AppUser.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        PasswordEncoder encoder =
                new BCryptPasswordEncoder(12);

        UserService userService = new UserService(
                userRepository,
                branchRepository,
                memberRepository,
                encoder,
                auditService
        );

        MemberAccountService accountService =
                new MemberAccountService(
                        userService,
                        userRepository,
                        new BranchScopeGuard()
                );

        memberService = new MemberService(
                memberRepository,
                branchRepository,
                new BranchScopeGuard(),
                auditService,
                accountService
        );
    }

    @Test
    @DisplayName("C1: createAccount=null (mặc định bật) → có tài khoản, mật khẩu tạm 10 ký tự")
    void createMacDinhCoTaiKhoan() {

        MemberCreateResponse response =
                memberService.create(
                        admin(),
                        request("Nguyen Van A", "0911111111",
                                "Member7@Gymfit.Local", null)
                );

        assertNotNull(response.member());
        assertEquals(Long.valueOf(77L), response.member().id());

        assertNotNull(
                response.account(),
                "mat dinh phai cap tai khoan"
        );

        assertEquals(
                "member7@gymfit.local",
                response.account().username()
        );

        assertEquals(
                10,
                response.account()
                        .temporaryPassword().length()
        );

        assertTrue(
                response.account().mustChangePassword()
        );

        // Đúng 1 AppUser được lưu
        verify(userRepository, times(1))
                .save(any(AppUser.class));

        // Cờ bắt buộc đổi mật khẩu được bật
        verify(auditService, times(1))
                .record(
                        any(),
                        eq("MEMBER_CREATED"),
                        any(),
                        any(),
                        any(),
                        any()
                );

        verify(auditService, times(1))
                .record(
                        any(),
                        eq("USER_CREATED"),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("C2: không có email → username theo mã hội viên")
    void createKhongCoEmail() {

        MemberCreateResponse response =
                memberService.create(
                        admin(),
                        request("Nguyen Van B", "0922222222",
                                null, null)
                );

        assertNotNull(response.account());

        assertEquals(
                response.member().memberCode()
                        .toLowerCase()
                        + "@member.gymfit.local",
                response.account().username()
        );

        verify(userRepository, times(1))
                .save(any(AppUser.class));
    }

    @Test
    @DisplayName("C3: createAccount=false → không lưu AppUser, account=null")
    void createKhongTaoTaiKhoan() {

        MemberCreateResponse response =
                memberService.create(
                        admin(),
                        request("Nguyen Van C", "0933333333",
                                "member8@gymfit.local", false)
                );

        assertNotNull(response.member());
        assertNull(response.account());

        verify(userRepository, never())
                .save(any(AppUser.class));

        verify(userRepository, never())
                .findByEmailIgnoreCase(anyString());

        verify(auditService, times(1))
                .record(
                        any(),
                        eq("MEMBER_CREATED"),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("C4: email đã thuộc tài khoản khác → user_email_exists, KHÔNG lưu hội viên")
    void createEmailDaTonTai() {

        when(userRepository.findByEmailIgnoreCase(
                "member7@gymfit.local"))
                .thenReturn(Optional.of(
                        AppUser.builder()
                                .id(99L)
                                .email("member7@gymfit.local")
                                .build()
                ));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> memberService.create(
                        admin(),
                        request("Nguyen Van D", "0944444444",
                                "member7@gymfit.local", null)
                )
        );

        assertEquals(
                "user_email_exists",
                exception.getCode()
        );

        // Nguyên tử: chưa có hội viên rác nào trong DB
        verify(memberRepository, never())
                .save(any(Member.class));

        verify(userRepository, never())
                .save(any(AppUser.class));
    }

    @Test
    @DisplayName("C5: MANAGER chi nhánh khác → branch_out_of_scope, không tạo user")
    void createManagerNgoaiChiNhanh() {

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> memberService.create(
                        managerOfBranch(2L),
                        request("Nguyen Van E", "0955555555",
                                "member9@gymfit.local", null)
                )
        );

        assertEquals(
                "branch_out_of_scope",
                exception.getCode()
        );

        verify(memberRepository, never())
                .save(any(Member.class));

        verify(userRepository, never())
                .save(any(AppUser.class));
    }

    @Test
    @DisplayName("C5b: MANAGER đúng chi nhánh → tạo được hội viên kèm tài khoản")
    void createManagerDungChiNhanh() {

        MemberCreateResponse response =
                memberService.create(
                        managerOfBranch(1L),
                        request("Nguyen Van F", "0966666666",
                                "member6@gymfit.local", null)
                );

        assertNotNull(response.account());

        verify(userRepository, times(1))
                .save(any(AppUser.class));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MemberCreateRequest request(
            String fullName,
            String phone,
            String email,
            Boolean createAccount
    ) {
        return new MemberCreateRequest(
                fullName,
                phone,
                email,
                1L,
                LocalDate.of(2000, 1, 1),
                createAccount
        );
    }

    private AppPrincipal admin() {
        return new AppPrincipal(
                AppUser.builder()
                        .id(100L)
                        .fullName("Quan tri vien")
                        .email("admin@gymfit.local")
                        .passwordHash("hash")
                        .roleCode(RoleCode.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .build()
        );
    }

    private AppPrincipal managerOfBranch(Long branchId) {
        return new AppPrincipal(
                AppUser.builder()
                        .id(101L)
                        .fullName("Quan ly chi nhanh")
                        .email("manager1@gymfit.local")
                        .passwordHash("hash")
                        .roleCode(RoleCode.BRANCH_MANAGER)
                        .status(UserStatus.ACTIVE)
                        .branchId(branchId)
                        .build()
        );
    }
}
