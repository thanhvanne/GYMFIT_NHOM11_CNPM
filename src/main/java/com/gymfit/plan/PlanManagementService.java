package com.gymfit.plan;

import com.gymfit.branch.*;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.MoneyUtil;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.plan.dto.*;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymfit.audit.AuditService;
import java.util.Map;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanManagementService {

    private final MembershipPlanRepository planRepository;
    private final PlanServiceRepository planServiceRepository;
    private final BranchRepository branchRepository;
    private final BranchServiceConfigRepository branchServiceConfigRepository;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PlanResponse> list(
            AppPrincipal principal,
            Long branchId,
            PlanStatus status
    ) {
        Long effectiveBranchId = branchId;

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            if (branchId != null) {
                branchScopeGuard.requireBranch(principal, branchId);
            }

            effectiveBranchId = principal.getBranchId();
        }

        List<MembershipPlan> plans;

        if (effectiveBranchId != null) {
            plans = status == null
                    ? planRepository
                    .findAllByBranchIdOrderByPriceAsc(effectiveBranchId)
                    : planRepository
                    .findAllByBranchIdAndStatusOrderByPriceAsc(
                            effectiveBranchId,
                            status
                    );
        } else {
            plans = status == null
                    ? planRepository.findAll()
                    : planRepository
                    .findAllByStatusOrderByBranchIdAscPriceAsc(status);
        }

        return plans.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(
            AppPrincipal principal,
            Long planId
    ) {
        MembershipPlan plan = requirePlan(planId);

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    plan.getBranchId()
            );
        }

        return toResponse(plan);
    }

    @Transactional
    public PlanResponse create(
            AppPrincipal principal,
            PlanCreateRequest request
    ) {
        if (principal.getRole() != RoleCode.ADMIN) {
            throw new ForbiddenException(
                    "plan_create_forbidden",
                    "Chỉ quản trị viên được tạo gói tập"
            );
        }

        Branch branch = requireActiveBranch(request.branchId());

        String code = request.planCode().trim().toUpperCase();

        if (planRepository.findByPlanCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException(
                    "plan_code_exists",
                    "Mã gói tập đã tồn tại"
            );
        }

        validateServices(
                branch.getId(),
                request.services()
        );

        MembershipPlan plan = MembershipPlan.builder()
                .branchId(branch.getId())
                .planCode(code)
                .name(request.name().trim())
                .tier(request.tier())
                .durationDays(request.durationDays())
                .price(MoneyUtil.normalize(request.price()))
                .description(normalizeNullable(request.description()))
                .status(PlanStatus.ACTIVE)
                .createdAtUtc(TimeUtil.now())
                .updatedAtUtc(TimeUtil.now())
                .build();

        MembershipPlan saved = planRepository.save(plan);

        saveServices(saved.getId(), request.services());

        auditService.record(
                principal.getUserId(),
                "PLAN_CREATED",
                "MEMBERSHIP_PLAN",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "planCode", saved.getPlanCode(),
                        "name", saved.getName()
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public PlanResponse update(
            AppPrincipal principal,
            Long planId,
            PlanUpdateRequest request
    ) {
        if (principal.getRole() != RoleCode.ADMIN) {
            throw new ForbiddenException(
                    "plan_update_forbidden",
                    "Chỉ quản trị viên được sửa gói tập"
            );
        }

        MembershipPlan plan = requirePlan(planId);

        String code = request.planCode().trim().toUpperCase();

        planRepository.findByPlanCodeIgnoreCase(code)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(planId)) {
                        throw new ConflictException(
                                "plan_code_exists",
                                "Mã gói tập đã tồn tại"
                        );
                    }
                });

        validateServices(
                plan.getBranchId(),
                request.services()
        );

        plan.setPlanCode(code);
        plan.setName(request.name().trim());
        plan.setTier(request.tier());
        plan.setDurationDays(request.durationDays());
        plan.setPrice(MoneyUtil.normalize(request.price()));
        plan.setDescription(normalizeNullable(request.description()));
        plan.setStatus(request.status());
        plan.setUpdatedAtUtc(TimeUtil.now());

        MembershipPlan saved = planRepository.save(plan);

        planServiceRepository.deleteAllByIdPlanId(planId);
        planServiceRepository.flush();

        saveServices(planId, request.services());

        auditService.record(
                principal.getUserId(),
                "PLAN_UPDATED",
                "MEMBERSHIP_PLAN",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "planCode", saved.getPlanCode(),
                        "status", saved.getStatus().name()
                )
        );

        return toResponse(saved);
    }

    @Transactional
    public PlanResponse updateStatus(
            AppPrincipal principal,
            Long planId,
            PlanStatusRequest request
    ) {
        if (principal.getRole() != RoleCode.ADMIN) {
            throw new ForbiddenException(
                    "plan_status_forbidden",
                    "Chỉ quản trị viên được thay đổi trạng thái gói"
            );
        }

        MembershipPlan plan = requirePlan(planId);

        plan.setStatus(request.status());
        plan.setUpdatedAtUtc(TimeUtil.now());

        return toResponse(planRepository.save(plan));
    }

    public MembershipPlan requirePlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "plan_not_found",
                        "Không tìm thấy gói tập"
                ));
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

    private void validateServices(
            Long branchId,
            Set<ServiceCode> services
    ) {
        for (ServiceCode service : services) {
            if (!branchServiceConfigRepository
                    .existsByIdBranchIdAndIdServiceCode(
                            branchId,
                            service
                    )) {
                throw new ConflictException(
                        "plan_service_not_supported",
                        "Gói chứa dịch vụ mà chi nhánh không hỗ trợ"
                );
            }
        }
    }

    private void saveServices(
            Long planId,
            Set<ServiceCode> services
    ) {
        List<PlanService> entities = services.stream()
                .map(service -> PlanService.builder()
                        .id(new PlanServiceId(planId, service))
                        .build())
                .toList();

        planServiceRepository.saveAll(entities);
    }

    private PlanResponse toResponse(MembershipPlan plan) {
        Set<ServiceCode> services = planServiceRepository
                .findAllByIdPlanId(plan.getId())
                .stream()
                .map(item -> item.getId().getServiceCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new PlanResponse(
                plan.getId(),
                plan.getBranchId(),
                plan.getPlanCode(),
                plan.getName(),
                plan.getTier(),
                plan.getDurationDays(),
                plan.getPrice(),
                plan.getDescription(),
                plan.getStatus(),
                services,
                plan.getCreatedAtUtc(),
                plan.getUpdatedAtUtc()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}