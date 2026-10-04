package com.gymfit.membership;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.ServiceCode;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.member.MemberStatus;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.plan.MembershipPlan;
import com.gymfit.plan.MembershipPlanRepository;
import com.gymfit.plan.PlanServiceRepository;
import com.gymfit.plan.PlanStatus;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MembershipServiceAccessRepository accessRepository;
    private final MembershipPlanRepository planRepository;
    private final PlanServiceRepository planServiceRepository;
    private final MemberRepository memberRepository;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;

    /**
     * Trả về toàn bộ gói đang có hiệu lực trong ngày hôm nay.
     * Một hội viên có thể có nhiều membership ACTIVE cùng lúc.
     */
    @Transactional(readOnly = true)
    public List<MembershipResponse> active(
            AppPrincipal principal,
            Long memberId
    ) {
        Member member = requireMember(memberId);
        requireScope(principal, member);

        LocalDate today = LocalDate.now(TimeUtil.VIETNAM);

        return membershipRepository
                .findAllByMemberIdAndStatusOrderByEndDateAscCreatedAtUtcDesc(
                        memberId,
                        MembershipStatus.ACTIVE
                )
                .stream()
                .filter(membership -> isEffectiveOn(membership, today))
                .map(this::toResponse)
                .toList();
    }

    /**
     * API tương thích cho các luồng cũ (chatbot/quản trị). Khi có nhiều gói,
     * phương thức này trả về gói sắp hết hạn nhất; giao diện hội viên dùng
     * {@link #active(AppPrincipal, Long)} để hiển thị đầy đủ tất cả gói.
     */
    @Transactional(readOnly = true)
    public MembershipResponse current(
            AppPrincipal principal,
            Long memberId
    ) {
        return active(principal, memberId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "active_membership_not_found",
                        "Hội viên chưa có gói tập đang hoạt động"
                ));
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> history(
            AppPrincipal principal,
            Long memberId
    ) {
        Member member = requireMember(memberId);
        requireScope(principal, member);

        return membershipRepository
                .findAllByMemberIdOrderByCreatedAtUtcDesc(memberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Membership activateFromPaidOrder(
            Long memberId,
            Long planId,
            Long orderId,
            Long actorUserId
    ) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new NotFoundException(
                        "member_not_found",
                        "Không tìm thấy hội viên"
                ));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ConflictException(
                    "member_inactive",
                    "Hội viên không hoạt động"
            );
        }

        MembershipPlan plan = planRepository
                .findById(planId)
                .orElseThrow(() -> new NotFoundException(
                        "plan_not_found",
                        "Không tìm thấy gói tập"
                ));

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new ConflictException(
                    "plan_inactive",
                    "Gói tập không còn hoạt động"
            );
        }

        /*
         * Chỉ dọn các membership đã quá hạn. Các gói còn hiệu lực không bị
         * REPLACED: mua gói mới là cộng thêm quyền sử dụng, không làm mất gói
         * cũ.
         */
        LocalDate startDate = LocalDate.now(TimeUtil.VIETNAM);
        List<Membership> existingActive = membershipRepository
                .findActiveForUpdate(memberId);

        existingActive.stream()
                .filter(existing -> existing.getEndDate().isBefore(startDate))
                .forEach(existing -> {
                    existing.setStatus(MembershipStatus.EXPIRED);
                    existing.setEndedAtUtc(TimeUtil.now());
                    membershipRepository.save(existing);
                });

        LocalDate endDate = startDate.plusDays(
                plan.getDurationDays() - 1L
        );

        Membership membership = Membership.builder()
                .memberId(memberId)
                .planId(planId)
                .orderId(orderId)
                .branchId(plan.getBranchId())
                .status(MembershipStatus.ACTIVE)
                .startDate(startDate)
                .endDate(endDate)
                .activatedAtUtc(TimeUtil.now())
                .endedAtUtc(null)
                .replacedByMembershipId(null)
                .createdAtUtc(TimeUtil.now())
                .build();

        Membership saved = membershipRepository
                .saveAndFlush(membership);

        List<MembershipServiceAccess> accesses =
                planServiceRepository
                        .findAllByIdPlanId(planId)
                        .stream()
                        .map(planService ->
                                MembershipServiceAccess.builder()
                                        .id(
                                                new MembershipServiceAccessId(
                                                        saved.getId(),
                                                        planService
                                                                .getId()
                                                                .getServiceCode()
                                                )
                                        )
                                        .build()
                        )
                        .toList();

        accessRepository.saveAll(accesses);

        auditService.record(
                actorUserId,
                "MEMBERSHIP_ACTIVATED",
                "MEMBERSHIP",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "memberId", memberId,
                        "planId", planId,
                        "orderId", orderId,
                        "coexistsWithActiveMemberships", !existingActive.isEmpty()
                )
        );

        return saved;
    }

    /**
     * Kiểm tra quyền đặt lịch/check-in theo ngày sử dụng cụ thể. Không dùng
     * một membership duy nhất nữa: chỉ cần một gói còn hiệu lực, đúng chi
     * nhánh và có đúng loại hình thể thao là đủ điều kiện.
     */
    @Transactional(readOnly = true)
    public Membership requireEligible(
            Long memberId,
            Long branchId,
            ServiceCode serviceCode
    ) {
        return requireEligible(
                memberId,
                branchId,
                serviceCode,
                LocalDate.now(TimeUtil.VIETNAM)
        );
    }

    @Transactional(readOnly = true)
    public Membership requireEligible(
            Long memberId,
            Long branchId,
            ServiceCode serviceCode,
            LocalDate usageDate
    ) {
        List<Membership> memberships = membershipRepository
                .findAllByMemberIdAndStatusOrderByEndDateAscCreatedAtUtcDesc(
                        memberId,
                        MembershipStatus.ACTIVE
                );

        if (memberships.isEmpty()) {
            throw new ConflictException(
                    "no_active_membership",
                    "Hội viên chưa có gói tập đang hoạt động"
            );
        }

        boolean hasMembershipAtBranch = false;
        boolean hasServiceAtBranch = false;

        for (Membership membership : memberships) {
            if (!membership.getBranchId().equals(branchId)) {
                continue;
            }

            hasMembershipAtBranch = true;

            if (!isEffectiveOn(membership, usageDate)) {
                continue;
            }

            if (accessRepository.existsByIdMembershipIdAndIdServiceCode(
                    membership.getId(),
                    serviceCode
            )) {
                hasServiceAtBranch = true;
                return membership;
            }
        }

        if (!hasMembershipAtBranch) {
            throw new ConflictException(
                    "membership_branch_mismatch",
                    "Gói tập không áp dụng tại chi nhánh này"
            );
        }

        if (!hasServiceAtBranch) {
            if (memberships.stream()
                    .filter(membership ->
                            membership.getBranchId().equals(branchId)
                    )
                    .noneMatch(membership ->
                            isEffectiveOn(membership, usageDate)
                    )) {
                throw new ConflictException(
                        "membership_not_effective",
                        "Gói tập không còn hiệu lực trong ngày đặt lịch"
                );
            }

            throw new ConflictException(
                    "membership_service_not_allowed",
                    "Gói tập không bao gồm loại hình thể thao này"
            );
        }

        throw new ConflictException(
                "membership_not_effective",
                "Gói tập không còn hiệu lực trong ngày đặt lịch"
        );
    }

    public MembershipResponse toResponse(
            Membership membership
    ) {
        Set<ServiceCode> services = accessRepository
                .findAllByIdMembershipId(
                        membership.getId()
                )
                .stream()
                .map(access ->
                        access.getId().getServiceCode()
                )
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );

        return new MembershipResponse(
                membership.getId(),
                membership.getMemberId(),
                membership.getPlanId(),
                membership.getOrderId(),
                membership.getBranchId(),
                effectiveStatus(membership),
                membership.getStartDate(),
                membership.getEndDate(),
                membership.getActivatedAtUtc(),
                membership.getEndedAtUtc(),
                membership.getReplacedByMembershipId(),
                services,
                membership.getCreatedAtUtc()
        );
    }

    private boolean isEffectiveOn(
            Membership membership,
            LocalDate date
    ) {
        return !date.isBefore(membership.getStartDate())
                && !date.isAfter(membership.getEndDate());
    }

    private MembershipStatus effectiveStatus(
            Membership membership
    ) {
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return membership.getStatus();
        }

        LocalDate today = LocalDate.now(TimeUtil.VIETNAM);

        if (today.isAfter(membership.getEndDate())) {
            return MembershipStatus.EXPIRED;
        }

        return MembershipStatus.ACTIVE;
    }

    private Member requireMember(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new NotFoundException(
                        "member_not_found",
                        "Không tìm thấy hội viên"
                ));
    }

    private void requireScope(
            AppPrincipal principal,
            Member member
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            if (principal.getBranchId() == null) {
                throw new ForbiddenException(
                        "manager_branch_missing",
                        "Tài khoản quản lý chưa được gán chi nhánh"
                );
            }

            if (principal.getBranchId()
                    .equals(member.getHomeBranchId())) {
                return;
            }

            boolean currentMembershipAtManagerBranch =
                    membershipRepository
                            .findAllByMemberIdAndStatusOrderByEndDateAscCreatedAtUtcDesc(
                                    member.getId(),
                                    MembershipStatus.ACTIVE
                            )
                            .stream()
                            .anyMatch(currentMembership ->
                                    currentMembership
                                            .getBranchId()
                                            .equals(principal.getBranchId())
                            );

            if (!currentMembershipAtManagerBranch) {
                throw new ForbiddenException(
                        "member_out_of_branch_scope",
                        "Hội viên nằm ngoài phạm vi chi nhánh"
                );
            }

            return;
        }

        branchScopeGuard.requireMemberSelf(
                principal,
                member.getId()
        );
    }
}
