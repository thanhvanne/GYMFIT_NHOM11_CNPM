package com.gymfit.member;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.BranchRepository;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.member.dto.MemberResponse;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F5 – cột trạng thái tài khoản trong {@code GET /api/v1/members}.
 */
class MemberServiceListTest {

    private MemberRepository memberRepository;
    private AppUserRepository appUserRepository;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        appUserRepository = mock(AppUserRepository.class);

        memberService = new MemberService(
                memberRepository,
                mock(BranchRepository.class),
                new BranchScopeGuard(),
                mock(AuditService.class),
                mock(MemberAccountService.class),
                appUserRepository
        );

        List<Member> members = List.of(
                member(1L, "GF00000001", "member1@gymfit.local"),
                member(2L, "GF00000002", "member2@gymfit.local"),
                member(3L, "GF00000003", "member3@gymfit.local")
        );

        when(memberRepository.findAll())
                .thenReturn(members);

        when(memberRepository.findById(1L))
                .thenReturn(java.util.Optional.of(members.get(0)));

        when(memberRepository.findById(2L))
                .thenReturn(java.util.Optional.of(members.get(1)));

        // Chỉ hội viên 2 có tài khoản
        when(appUserRepository.findAllByMemberIdIn(any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Collection<Long> ids =
                            invocation.getArgument(0);

                    return ids.contains(2L)
                            ? List.of(
                            AppUser.builder()
                                    .id(50L)
                                    .memberId(2L)
                                    .email("member2@gymfit.local")
                                    .roleCode(RoleCode.MEMBER)
                                    .status(UserStatus.ACTIVE)
                                    .build()
                    )
                            : List.of();
                });
    }

    @Test
    @DisplayName("3 hội viên, 1 có tài khoản → hasAccount/accountUsername đúng từng dòng")
    void listDungTrangThaiTaiKhoan() {

        List<MemberResponse> responses =
                memberService.list(admin(), null, null);

        assertEquals(3, responses.size());

        MemberResponse khongCo = responses.get(0);
        assertEquals(Long.valueOf(1L), khongCo.id());
        assertFalse(khongCo.hasAccount());
        assertNull(khongCo.accountUsername());

        MemberResponse co = responses.get(1);
        assertEquals(Long.valueOf(2L), co.id());
        assertTrue(co.hasAccount());
        assertEquals(
                "member2@gymfit.local",
                co.accountUsername()
        );

        MemberResponse khongCo2 = responses.get(2);
        assertFalse(khongCo2.hasAccount());
        assertNull(khongCo2.accountUsername());

        // Repository tài khoản chỉ gọi MỘT lần cho 3 hội viên
        verify(appUserRepository, times(1))
                .findAllByMemberIdIn(any());
    }

    @Test
    @DisplayName("get(1) cũng trả hasAccount=false, get(2) trả true")
    void getCoTrangThaiTaiKhoan() {

        assertFalse(
                memberService.get(admin(), 1L).hasAccount()
        );

        MemberResponse co = memberService.get(admin(), 2L);

        assertTrue(co.hasAccount());
        assertEquals(
                "member2@gymfit.local",
                co.accountUsername()
        );
    }

    @Test
    @DisplayName("Danh sách rỗng → không gọi repository tài khoản")
    void listRong() {

        when(memberRepository.findAll())
                .thenReturn(List.of());

        assertTrue(
                memberService.list(admin(), null, null).isEmpty()
        );

        verify(appUserRepository, never())
                .findAllByMemberIdIn(any());
    }

    // ------------------------------------------------------------------

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

    private Member member(
            Long id,
            String memberCode,
            String email
    ) {
        return Member.builder()
                .id(id)
                .memberCode(memberCode)
                .fullName("Hoi vien " + id)
                .phone("09000000" + id)
                .email(email)
                .homeBranchId(1L)
                .status(MemberStatus.ACTIVE)
                .createdAtUtc(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAtUtc(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
