package com.gymfit.order;

import com.gymfit.audit.AuditService;
import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.CodeGenerator;
import com.gymfit.common.util.MoneyUtil;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.member.MemberStatus;
import com.gymfit.order.dto.MemberPlanPurchaseRequest;
import com.gymfit.order.dto.OrderResponse;
import com.gymfit.plan.MembershipPlan;
import com.gymfit.plan.MembershipPlanRepository;
import com.gymfit.plan.PlanStatus;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberPurchaseService {

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final MembershipPlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final BranchRepository branchRepository;
    private final OrderService orderService;
    private final AuditService auditService;

    @Transactional
    public OrderResponse purchasePlan(
            AppPrincipal principal,
            MemberPlanPurchaseRequest request
    ) {
        if (principal.getRole() != RoleCode.MEMBER
                || principal.getMemberId() == null) {
            throw new ForbiddenException(
                    "member_purchase_forbidden",
                    "Chỉ tài khoản hội viên được sử dụng chức năng này"
            );
        }

        Member member = memberRepository
                .findById(principal.getMemberId())
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
                .findById(request.planId())
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

        Branch branch = branchRepository
                .findById(plan.getBranchId())
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

        BigDecimal price =
                MoneyUtil.normalize(plan.getPrice());

        SalesOrder order = SalesOrder.builder()
                .orderCode(
                        CodeGenerator.generate("ORD")
                )
                .branchId(plan.getBranchId())
                .memberId(member.getId())
                .status(OrderStatus.PENDING_PAYMENT)
                .subtotal(price)
                .total(price)
                .createdByUserId(
                        principal.getUserId()
                )
                .createdAtUtc(TimeUtil.now())
                .paidAtUtc(null)
                .build();

        SalesOrder savedOrder =
                orderRepository.saveAndFlush(order);

        SalesOrderItem item =
                SalesOrderItem.builder()
                        .orderId(savedOrder.getId())
                        .itemType(OrderItemType.PLAN)
                        .planId(plan.getId())
                        .productId(null)
                        .nameSnapshot(plan.getName())
                        .unitPrice(price)
                        .quantity(1)
                        .lineTotal(price)
                        .build();

        itemRepository.save(item);

        auditService.record(
                principal.getUserId(),
                "ORDER_CREATED",
                "SALES_ORDER",
                savedOrder.getId(),
                savedOrder.getBranchId(),
                Map.of(
                        "orderCode",
                        savedOrder.getOrderCode(),
                        "memberId",
                        member.getId(),
                        "planId",
                        plan.getId(),
                        "source",
                        "MEMBER"
                )
        );

        return orderService.get(
                principal,
                savedOrder.getId()
        );
    }
}