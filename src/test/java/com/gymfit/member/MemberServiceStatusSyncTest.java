package com.gymfit.member;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.member.dto.MemberResponse;
import com.gymfit.member.dto.MemberUpdateRequest;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F8.1 – đồng bộ trạng thái hội viên ↔ tài khoản đăng nhập (D6):
 * {@code INACTIVE} ⇒ tài khoản {@code DISABLED}, trở lại {@code ACTIVE} ⇒ mở lại.
 */
class MemberServiceStatusSyncTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long USER_ID = 50L;

    private MemberRepository memberRepository;
    private BranchRepository branchRepository;
    private AppUserRepository appUserRepository;
    private AuditService auditService;
    private MemberService memberService;

    private Member member;
    private AppUser user;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        branchRepository = mock(BranchRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        auditService = mock(AuditService.class);

        memberService = new MemberService(
                memberRepository,
                branchRepository,
                new BranchScopeGuard(),
                auditService,
                mock(MemberAccountService.class),
                appUserRepository
        );

        member = Member.builder()
                .id(MEMBER_ID)
                .memberCode("GF00000001")
                .fullName("Hoi vien 1")
                .phone("090000001")
                .email("member1@gymfit.local")
                .homeBranchId(1L)
                .status(MemberStatus.ACTIVE)
                .createdAtUtc(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAtUtc(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        user = AppUser.builder()
                .id(USER_ID)
                .memberId(MEMBER_ID)
                .email("member1@gymfit.local")
                .roleCode(RoleCode.MEMBER)
                .status(UserStatus.ACTIVE)
                .build();

        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(memberRepository.findByPhone(any()))
                .thenReturn(Optional.empty());

        when(memberRepository.findByEmailIgnoreCase(any()))
                .thenReturn(Optional.empty());

        when(branchRepository.findById(1L))
                .thenReturn(Optional.of(
                        Branch.builder()
                                .id(1L)
                                .status(BranchStatus.ACTIVE)
                                .build()
                ));

        when(appUserRepository.findByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(user));

        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(appUserRepository.findAllByMemberIdIn(any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("ACTIVE → INACTIVE: tài khoản bị DISABLED + audit USER_UPDATED")
    void tatTaiKhoanKhiHoiVienInactive() {

        MemberResponse response =
                chuyenTrangThai(MemberStatus.INACTIVE);

        assertEquals(MemberStatus.INACTIVE, member.getStatus());
        assertEquals(MemberStatus.INACTIVE, response.status());

        assertEquals(UserStatus.DISABLED, user.getStatus());
        verify(appUserRepository).save(user);

        verify(auditService).record(
                eq(100L),
                eq("USER_UPDATED"),
                eq("APP_USER"),
                eq(USER_ID),
                eq(1L),
                anyMap()
        );
    }

    @Test
    @DisplayName("INACTIVE → ACTIVE: mở lại tài khoản + audit USER_UPDATED")
    void moLaiTaiKhoanKhiHoiVienActive() {

        member.setStatus(MemberStatus.INACTIVE);
        user.setStatus(UserStatus.DISABLED);

        MemberResponse response =
                chuyenTrangThai(MemberStatus.ACTIVE);

        assertEquals(MemberStatus.ACTIVE, response.status());

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(appUserRepository).save(user);

        verify(auditService).record(
                eq(100L),
                eq("USER_UPDATED"),
                eq("APP_USER"),
                eq(USER_ID),
                eq(1L),
                anyMap()
        );
    }

    @Test
    @DisplayName("Trạng thái không đổi → không đụng tới tài khoản")
    void khongDoiTrangThaiKhongDongTaiKhoan() {

        chuyenTrangThai(MemberStatus.ACTIVE);

        assertEquals(MemberStatus.ACTIVE, member.getStatus());

        verify(appUserRepository, never())
                .findByMemberId(any());

        verify(appUserRepository, never())
                .save(any(AppUser.class));

        verify(auditService, never()).record(
                any(),
                eq("USER_UPDATED"),
                any(),
                any(),
                any(),
                anyMap()
        );
    }

    @Test
    @DisplayName("Hội viên chưa có tài khoản → bỏ qua, không lỗi")
    void hoiVienChuaCoTaiKhoan() {

        when(appUserRepository.findByMemberId(MEMBER_ID))
                .thenReturn(Optional.empty());

        MemberResponse response =
                chuyenTrangThai(MemberStatus.INACTIVE);

        assertEquals(MemberStatus.INACTIVE, response.status());

        verify(appUserRepository, never())
                .save(any(AppUser.class));

        verify(auditService, never()).record(
                any(),
                eq("USER_UPDATED"),
                any(),
                any(),
                any(),
                anyMap()
        );
    }

    // ------------------------------------------------------------------

    private MemberResponse chuyenTrangThai(MemberStatus moi) {
        return memberService.update(
                admin(),
                MEMBER_ID,
                new MemberUpdateRequest(
                        "Hoi vien 1",
                        "090000001",
                        "member1@gymfit.local",
                        1L,
                        null,
                        moi
                )
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
}
