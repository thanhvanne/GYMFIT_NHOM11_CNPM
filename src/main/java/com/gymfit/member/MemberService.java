package com.gymfit.member;

import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.dto.MemberCreateRequest;
import com.gymfit.member.dto.MemberCreateResponse;
import com.gymfit.member.dto.AccountCredentialsResponse;
import com.gymfit.member.dto.MemberResponse;
import com.gymfit.member.dto.MemberUpdateRequest;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymfit.audit.AuditService;
import java.util.Map;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BranchRepository branchRepository;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;
    private final MemberAccountService memberAccountService;
    private final AppUserRepository appUserRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<MemberResponse> list(
            AppPrincipal principal,
            String query,
            MemberStatus status
    ) {
        List<Member> base;

        if (principal.getRole() == RoleCode.ADMIN) {
            base = status == null
                    ? memberRepository.findAll()
                    : memberRepository.findAllByStatusOrderByCreatedAtUtcDesc(status);
        } else if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            if (principal.getBranchId() == null) {
                throw new ForbiddenException(
                        "manager_branch_missing",
                        "Tài khoản quản lý chưa được gán chi nhánh"
                );
            }

            base = memberRepository
                    .findAllByHomeBranchIdOrderByCreatedAtUtcDesc(
                            principal.getBranchId()
                    );
        } else {
            if (principal.getMemberId() == null) {
                throw new ForbiddenException(
                        "member_scope_missing",
                        "Tài khoản chưa liên kết hội viên"
                );
            }

            Member self = requireMember(principal.getMemberId());
            base = List.of(self);
        }

        String normalized = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        List<Member> filtered = base.stream()
                .filter(member ->
                        status == null || member.getStatus() == status
                )
                .filter(member ->
                        normalized.isEmpty()
                                || contains(member.getFullName(), normalized)
                                || contains(member.getPhone(), normalized)
                                || contains(member.getMemberCode(), normalized)
                                || contains(member.getEmail(), normalized)
                )
                .toList();

        // F5: một câu lệnh cho tất cả tài khoản (không N+1)
        Map<Long, AppUser> accounts = accountsByMemberId(filtered);

        return filtered.stream()
                .map(member -> toResponse(member, accounts))
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse get(
            AppPrincipal principal,
            Long memberId
    ) {
        Member member = requireMember(memberId);
        requireReadScope(principal, member);
        return toResponse(member, accountsByMemberId(List.of(member)));
    }

    @Transactional
    public MemberCreateResponse create(
            AppPrincipal principal,
            MemberCreateRequest request
    ) {
        if (principal.getRole() == RoleCode.MEMBER) {
            throw new ForbiddenException(
                    "member_create_forbidden",
                    "Hội viên không thể tạo hồ sơ hội viên"
            );
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    request.homeBranchId()
            );
        }

        Branch branch = requireActiveBranch(request.homeBranchId());

        ensurePhoneAvailable(request.phone(), null);
        ensureEmailAvailable(request.email(), null);

        boolean wantAccount = request.createAccount() == null
                || request.createAccount();

        String memberCode = generateMemberCode();

        // Kiểm trùng tên đăng nhập TRƯỚC khi lưu hội viên:
        // nếu trùng (user_email_exists) thì chưa có gì được ghi vào DB.
        if (wantAccount) {
            memberAccountService.assertUsernameAvailable(
                    request.email(),
                    memberCode
            );
        }

        Member member = Member.builder()
                .memberCode(memberCode)
                .fullName(request.fullName().trim())
                .phone(request.phone().trim())
                .email(normalizeEmail(request.email()))
                .homeBranchId(branch.getId())
                .dateOfBirth(request.dateOfBirth())
                .status(MemberStatus.ACTIVE)
                .createdAtUtc(TimeUtil.now())
                .updatedAtUtc(TimeUtil.now())
                .build();

        Member saved = memberRepository.save(member);

        auditService.record(
                principal.getUserId(),
                "MEMBER_CREATED",
                "MEMBER",
                saved.getId(),
                saved.getHomeBranchId(),
                Map.of(
                        "memberCode", saved.getMemberCode(),
                        "fullName", saved.getFullName()
                )
        );

        // Cùng transaction: lỗi ở bước cấp tài khoản ⇒ rollback cả hội viên.
        AccountCredentialsResponse account = wantAccount
                ? memberAccountService.issue(principal, saved)
                : null;

        return new MemberCreateResponse(
                toResponse(saved),
                account
        );
    }

    /**
     * {@code POST /api/v1/members/{id}/account} – cấp tài khoản cho hội viên
     * đã có (F6 mục 5). Quyền ADMIN/BRANCH_MANAGER + kiểm chi nhánh ở service.
     */
    @Transactional
    public AccountCredentialsResponse issueAccount(
            AppPrincipal principal,
            Long memberId
    ) {
        Member member = requireMember(memberId);

        return memberAccountService.issue(
                principal,
                member
        );
    }

    /**
     * {@code POST /api/v1/members/{id}/account/reset-password} – sinh mật khẩu
     * tạm mới, bật cờ bắt buộc đổi mật khẩu lần đầu (D3).
     */
    @Transactional
    public AccountCredentialsResponse resetAccountPassword(
            AppPrincipal principal,
            Long memberId
    ) {
        Member member = requireMember(memberId);

        return memberAccountService.resetPassword(
                principal,
                member
        );
    }

    @Transactional
    public MemberResponse update(
            AppPrincipal principal,
            Long memberId,
            MemberUpdateRequest request
    ) {
        Member member = requireMember(memberId);

        if (principal.getRole() == RoleCode.MEMBER) {
            throw new ForbiddenException(
                    "member_update_forbidden",
                    "Hội viên không thể thay đổi hồ sơ quản trị"
            );
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    member.getHomeBranchId()
            );

            branchScopeGuard.requireBranch(
                    principal,
                    request.homeBranchId()
            );
        }

        requireActiveBranch(request.homeBranchId());

        ensurePhoneAvailable(request.phone(), memberId);
        ensureEmailAvailable(request.email(), memberId);

        member.setFullName(request.fullName().trim());
        member.setPhone(request.phone().trim());
        member.setEmail(normalizeEmail(request.email()));
        member.setHomeBranchId(request.homeBranchId());
        member.setDateOfBirth(request.dateOfBirth());
        member.setStatus(request.status());
        member.setUpdatedAtUtc(TimeUtil.now());

        Member saved = memberRepository.save(member);

        auditService.record(
                principal.getUserId(),
                "MEMBER_UPDATED",
                "MEMBER",
                saved.getId(),
                saved.getHomeBranchId(),
                Map.of(
                        "memberCode", saved.getMemberCode(),
                        "status", saved.getStatus().name()
                )
        );

        return toResponse(saved);
    }

    public Member requireMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "member_not_found",
                        "Không tìm thấy hội viên"
                ));
    }

    private void requireReadScope(
            AppPrincipal principal,
            Member member
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    member.getHomeBranchId()
            );
            return;
        }

        branchScopeGuard.requireMemberSelf(
                principal,
                member.getId()
        );
    }

    private Branch requireActiveBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException(
                        "branch_not_found",
                        "Không tìm thấy chi nhánh"
                ));

        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new ConflictException(
                    "branch_inactive",
                    "Chi nhánh không hoạt động"
            );
        }

        return branch;
    }

    private void ensurePhoneAvailable(
            String phone,
            Long currentId
    ) {
        memberRepository.findByPhone(phone.trim())
                .ifPresent(existing -> {
                    if (currentId == null
                            || !existing.getId().equals(currentId)) {
                        throw new ConflictException(
                                "phone_exists",
                                "Số điện thoại đã được sử dụng"
                        );
                    }
                });
    }

    private void ensureEmailAvailable(
            String email,
            Long currentId
    ) {
        String normalized = normalizeEmail(email);

        if (normalized == null) {
            return;
        }

        memberRepository.findByEmailIgnoreCase(normalized)
                .ifPresent(existing -> {
                    if (currentId == null
                            || !existing.getId().equals(currentId)) {
                        throw new ConflictException(
                                "member_email_exists",
                                "Email hội viên đã được sử dụng"
                        );
                    }
                });
    }

    private String generateMemberCode() {
        for (int i = 0; i < 20; i++) {
            String code = "GF"
                    + String.format(
                    "%08d",
                    secureRandom.nextInt(100_000_000)
            );

            if (memberRepository
                    .findByMemberCodeIgnoreCase(code)
                    .isEmpty()) {
                return code;
            }
        }

        throw new ConflictException(
                "member_code_generation_failed",
                "Không thể tạo mã hội viên"
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String query) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private MemberResponse toResponse(Member member) {
        return toResponse(
                member,
                accountsByMemberId(List.of(member))
        );
    }

    /**
     * F5 – gắn cờ {@code hasAccount} + {@code accountUsername} (đã nạp sẵn
     * bằng {@link #accountsByMemberId} để không query từng dòng).
     */
    private MemberResponse toResponse(
            Member member,
            Map<Long, AppUser> accounts
    ) {
        AppUser account = member.getId() == null
                ? null
                : accounts.get(member.getId());

        return new MemberResponse(
                member.getId(),
                member.getMemberCode(),
                member.getFullName(),
                member.getPhone(),
                member.getEmail(),
                member.getHomeBranchId(),
                member.getDateOfBirth(),
                member.getStatus(),
                member.getCreatedAtUtc(),
                member.getUpdatedAtUtc(),
                account != null,
                account == null ? null : account.getEmail()
        );
    }

    /**
     * Nạp tài khoản của N hội viên trong 1 câu lệnh.
     */
    private Map<Long, AppUser> accountsByMemberId(
            List<Member> members
    ) {
        List<Long> memberIds = members.stream()
                .map(Member::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (memberIds.isEmpty()) {
            return Map.of();
        }

        return appUserRepository
                .findAllByMemberIdIn(memberIds)
                .stream()
                .filter(user -> user.getMemberId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        AppUser::getMemberId,
                        user -> user,
                        (first, second) -> first
                ));
    }
}