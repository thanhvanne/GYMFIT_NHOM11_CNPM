package com.gymfit.checkin;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.*;
import com.gymfit.checkin.dto.CheckInResponse;
import com.gymfit.checkin.dto.ManualCheckInRequest;
import com.gymfit.checkin.dto.QrCheckInRequest;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.member.MemberStatus;
import com.gymfit.membership.Membership;
import com.gymfit.membership.MembershipService;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final Duration DUPLICATE_WINDOW =
            Duration.ofMinutes(5);

    private final CheckInRepository checkInRepository;
    private final QrTokenUseRepository qrTokenUseRepository;
    private final QrTokenService qrTokenService;
    private final MemberRepository memberRepository;
    private final BranchRepository branchRepository;
    private final BranchServiceConfigRepository serviceConfigRepository;
    private final MembershipService membershipService;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;

    @Transactional
    public CheckInResponse manual(
            AppPrincipal principal,
            ManualCheckInRequest request
    ) {
        Long branchId = resolveStaffBranch(
                principal,
                request.branchId()
        );

        requireBranchScope(
                principal,
                branchId
        );

        Member member = findMember(
                request.memberIdentifier()
        );

        if (member == null) {
            return reject(
                    null,
                    branchId,
                    request.serviceCode(),
                    CheckInMethod.MANUAL,
                    "MEMBER_NOT_FOUND",
                    principal.getUserId()
            );
        }

        return performEligibilityCheck(
                member,
                branchId,
                request.serviceCode(),
                CheckInMethod.MANUAL,
                principal.getUserId()
        );
    }

    @Transactional
    public CheckInResponse qr(
            AppPrincipal principal,
            QrCheckInRequest request
    ) {
        Long branchId = resolveStaffBranch(
                principal,
                request.branchId()
        );

        requireBranchScope(
                principal,
                branchId
        );

        QrTokenPayload payload;

        try {
            payload = qrTokenService.parse(
                    request.token().trim()
            );
        } catch (Exception exception) {
            return reject(
                    null,
                    branchId,
                    request.serviceCode(),
                    CheckInMethod.QR,
                    "QR_EXPIRED_OR_INVALID",
                    principal.getUserId()
            );
        }

        Member member = memberRepository
                .findByIdForUpdate(
                        payload.memberId()
                )
                .orElse(null);

        if (member == null) {
            return reject(
                    payload.memberId(),
                    branchId,
                    request.serviceCode(),
                    CheckInMethod.QR,
                    "MEMBER_NOT_FOUND",
                    principal.getUserId()
            );
        }

        if (qrTokenUseRepository.existsByTokenJti(
                payload.jti()
        )) {
            return reject(
                    member.getId(),
                    branchId,
                    request.serviceCode(),
                    CheckInMethod.QR,
                    "QR_REPLAY",
                    principal.getUserId()
            );
        }

        qrTokenUseRepository.saveAndFlush(
                QrTokenUse.builder()
                        .tokenJti(payload.jti())
                        .memberId(member.getId())
                        .usedAtUtc(TimeUtil.now())
                        .build()
        );

        return performEligibilityCheckLocked(
                member,
                branchId,
                request.serviceCode(),
                CheckInMethod.QR,
                principal.getUserId()
        );
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> list(
            AppPrincipal principal
    ) {
        List<CheckIn> checkIns;

        if (principal.getRole() == RoleCode.ADMIN) {
            checkIns = checkInRepository
                    .findAllByOrderByCreatedAtUtcDesc();

        } else if (
                principal.getRole()
                        == RoleCode.BRANCH_MANAGER
        ) {
            checkIns = checkInRepository
                    .findAllByBranchIdOrderByCreatedAtUtcDesc(
                            principal.getBranchId()
                    );

        } else {
            if (principal.getMemberId() == null) {
                throw new ForbiddenException(
                        "member_scope_missing",
                        "Tài khoản chưa liên kết hội viên"
                );
            }

            checkIns = checkInRepository
                    .findAllByMemberIdOrderByCreatedAtUtcDesc(
                            principal.getMemberId()
                    );
        }

        return checkIns.stream()
                .map(this::toResponse)
                .toList();
    }

    private CheckInResponse performEligibilityCheck(
            Member member,
            Long branchId,
            ServiceCode serviceCode,
            CheckInMethod method,
            Long performedBy
    ) {
        Member lockedMember = memberRepository
                .findByIdForUpdate(
                        member.getId()
                )
                .orElseThrow(() ->
                        new NotFoundException(
                                "member_not_found",
                                "Không tìm thấy hội viên"
                        )
                );

        return performEligibilityCheckLocked(
                lockedMember,
                branchId,
                serviceCode,
                method,
                performedBy
        );
    }

    private CheckInResponse performEligibilityCheckLocked(
            Member member,
            Long branchId,
            ServiceCode serviceCode,
            CheckInMethod method,
            Long performedBy
    ) {
        if (member.getStatus()
                != MemberStatus.ACTIVE) {

            return reject(
                    member.getId(),
                    branchId,
                    serviceCode,
                    method,
                    "MEMBER_INACTIVE",
                    performedBy
            );
        }

        Branch branch = branchRepository
                .findById(branchId)
                .orElse(null);

        if (branch == null
                || branch.getStatus()
                != BranchStatus.ACTIVE) {

            return reject(
                    member.getId(),
                    branchId,
                    serviceCode,
                    method,
                    "BRANCH_INACTIVE",
                    performedBy
            );
        }

        if (!serviceConfigRepository
                .existsByIdBranchIdAndIdServiceCode(
                        branchId,
                        serviceCode
                )) {

            return reject(
                    member.getId(),
                    branchId,
                    serviceCode,
                    method,
                    "SERVICE_NOT_SUPPORTED",
                    performedBy
            );
        }

        Membership membership;

        try {
            membership =
                    membershipService.requireEligible(
                            member.getId(),
                            branchId,
                            serviceCode
                    );
        } catch (ConflictException exception) {

            String reason = switch (
                    exception.getCode()
                    ) {
                case "no_active_membership",
                     "membership_not_effective" ->
                        "NO_ACTIVE_MEMBERSHIP";

                case "membership_service_not_allowed" ->
                        "SERVICE_NOT_INCLUDED";

                case "membership_branch_mismatch" ->
                        "MEMBERSHIP_BRANCH_MISMATCH";

                default ->
                        "MEMBERSHIP_INVALID";
            };

            return reject(
                    member.getId(),
                    branchId,
                    serviceCode,
                    method,
                    reason,
                    performedBy
            );
        }

        Instant duplicateAfter =
                TimeUtil.now()
                        .minus(
                                DUPLICATE_WINDOW
                        );

        boolean duplicate =
                checkInRepository
                        .existsByMemberIdAndBranchIdAndServiceCodeAndResultAndCreatedAtUtcAfter(
                                member.getId(),
                                branchId,
                                serviceCode,
                                CheckInResult.ACCEPTED,
                                duplicateAfter
                        );

        if (duplicate) {
            return reject(
                    member.getId(),
                    branchId,
                    serviceCode,
                    method,
                    "DUPLICATE_CHECKIN",
                    performedBy
            );
        }

        CheckIn checkIn = CheckIn.builder()
                .memberId(member.getId())
                .membershipId(
                        membership.getId()
                )
                .branchId(branchId)
                .serviceCode(serviceCode)
                .method(method)
                .result(
                        CheckInResult.ACCEPTED
                )
                .reason(null)
                .performedByUserId(
                        performedBy
                )
                .createdAtUtc(
                        TimeUtil.now()
                )
                .build();

        CheckIn saved =
                checkInRepository.save(
                        checkIn
                );

        auditService.record(
                performedBy,
                "CHECKIN_ACCEPTED",
                "CHECK_IN",
                saved.getId(),
                branchId,
                Map.of(
                        "memberId",
                        member.getId(),
                        "serviceCode",
                        serviceCode.name(),
                        "method",
                        method.name()
                )
        );

        return toResponse(saved);
    }

    private CheckInResponse reject(
            Long memberId,
            Long branchId,
            ServiceCode serviceCode,
            CheckInMethod method,
            String reason,
            Long performedBy
    ) {
        CheckIn checkIn = CheckIn.builder()
                .memberId(memberId)
                .membershipId(null)
                .branchId(branchId)
                .serviceCode(serviceCode)
                .method(method)
                .result(
                        CheckInResult.REJECTED
                )
                .reason(reason)
                .performedByUserId(
                        performedBy
                )
                .createdAtUtc(
                        TimeUtil.now()
                )
                .build();

        CheckIn saved =
                checkInRepository.save(
                        checkIn
                );

        Map<String, Object> details =
                new java.util.LinkedHashMap<>();

        if (memberId != null) {
            details.put(
                    "memberId",
                    memberId
            );
        }

        details.put(
                "serviceCode",
                serviceCode.name()
        );

        details.put(
                "method",
                method.name()
        );

        details.put(
                "reason",
                reason
        );

        auditService.record(
                performedBy,
                "CHECKIN_REJECTED",
                "CHECK_IN",
                saved.getId(),
                branchId,
                details
        );

        return toResponse(saved);
    }

    private Member findMember(
            String identifier
    ) {
        String value =
                identifier.trim();

        return memberRepository
                .findByMemberCodeIgnoreCase(
                        value
                )
                .or(() ->
                        memberRepository
                                .findByPhone(
                                        value
                                )
                )
                .orElse(null);
    }

    private Long resolveStaffBranch(
            AppPrincipal principal,
            Long requestedBranchId
    ) {
        if (principal.getRole()
                == RoleCode.ADMIN) {

            if (requestedBranchId == null) {
                throw new ConflictException(
                        "branch_required",
                        "Vui lòng chọn chi nhánh"
                );
            }

            return requestedBranchId;
        }

        if (principal.getRole()
                == RoleCode.BRANCH_MANAGER) {

            if (principal.getBranchId()
                    == null) {
                throw new ForbiddenException(
                        "manager_branch_missing",
                        "Tài khoản quản lý chưa được gán chi nhánh"
                );
            }

            return principal.getBranchId();
        }

        throw new ForbiddenException(
                "checkin_forbidden",
                "Bạn không có quyền thực hiện check-in"
        );
    }

    private void requireBranchScope(
            AppPrincipal principal,
            Long branchId
    ) {
        if (principal.getRole()
                == RoleCode.BRANCH_MANAGER) {

            branchScopeGuard.requireBranch(
                    principal,
                    branchId
            );
        }
    }

    private CheckInResponse toResponse(
            CheckIn checkIn
    ) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getMemberId(),
                checkIn.getMembershipId(),
                checkIn.getBranchId(),
                checkIn.getServiceCode(),
                checkIn.getMethod(),
                checkIn.getResult(),
                checkIn.getReason(),
                checkIn.getPerformedByUserId(),
                checkIn.getCreatedAtUtc()
        );
    }
}