package com.gymfit.order;

import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.CodeGenerator;
import com.gymfit.common.util.MoneyUtil;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.member.MemberStatus;
import com.gymfit.order.dto.*;
import com.gymfit.plan.MembershipPlan;
import com.gymfit.plan.MembershipPlanRepository;
import com.gymfit.plan.PlanStatus;
import com.gymfit.product.Product;
import com.gymfit.product.ProductRepository;
import com.gymfit.product.ProductStatus;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymfit.audit.AuditService;
import java.util.Map;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final MembershipPlanRepository planRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final BranchRepository branchRepository;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;

    @Transactional
    public OrderResponse create(
            AppPrincipal principal,
            OrderCreateRequest request
    ) {
        if (principal.getRole() == RoleCode.MEMBER) {
            throw new ForbiddenException(
                    "order_create_forbidden",
                    "Hội viên không được tạo đơn bán hàng tại quầy"
            );
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    request.branchId()
            );
        }

        Branch branch = branchRepository
                .findById(request.branchId())
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

        Member member = null;

        if (request.memberId() != null) {
            member = memberRepository
                    .findById(request.memberId())
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
        }

        long planCount = request.items().stream()
                .filter(item ->
                        item.itemType() == OrderItemType.PLAN
                )
                .count();

        if (planCount > 1) {
            throw new ConflictException(
                    "multiple_plans_not_allowed",
                    "Mỗi đơn hàng chỉ được chứa tối đa một gói tập"
            );
        }

        if (planCount > 0 && member == null) {
            throw new ConflictException(
                    "plan_member_required",
                    "Đơn mua gói tập bắt buộc phải có hội viên"
            );
        }

        SalesOrder order = SalesOrder.builder()
                .orderCode(CodeGenerator.generate("ORD"))
                .branchId(branch.getId())
                .memberId(member == null ? null : member.getId())
                .status(OrderStatus.PENDING_PAYMENT)
                .subtotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .createdByUserId(principal.getUserId())
                .createdAtUtc(TimeUtil.now())
                .paidAtUtc(null)
                .build();

        SalesOrder savedOrder = orderRepository.save(order);

        List<SalesOrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItemRequest requestItem : request.items()) {
            SalesOrderItem item = createItem(
                    savedOrder,
                    requestItem
            );

            items.add(item);
            subtotal = subtotal.add(item.getLineTotal());
        }

        itemRepository.saveAll(items);

        savedOrder.setSubtotal(MoneyUtil.normalize(subtotal));
        savedOrder.setTotal(MoneyUtil.normalize(subtotal));

        orderRepository.save(savedOrder);

        auditService.record(
                principal.getUserId(),
                "ORDER_CREATED",
                "SALES_ORDER",
                savedOrder.getId(),
                savedOrder.getBranchId(),
                Map.of(
                        "orderCode", savedOrder.getOrderCode(),
                        "total", savedOrder.getTotal().toPlainString()
                )
        );

        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(
            AppPrincipal principal,
            Long orderId
    ) {
        SalesOrder order = requireOrder(orderId);
        requireScope(principal, order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(
            AppPrincipal principal
    ) {
        List<SalesOrder> orders;

        if (principal.getRole() == RoleCode.ADMIN) {
            orders = orderRepository
                    .findAllByOrderByCreatedAtUtcDesc();
        } else if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            orders = orderRepository
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

            orders = orderRepository
                    .findAllByMemberIdOrderByCreatedAtUtcDesc(
                            principal.getMemberId()
                    );
        }

        return orders.stream()
                .map(this::toResponse)
                .toList();
    }

    public SalesOrder requireOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "order_not_found",
                        "Không tìm thấy đơn hàng"
                ));
    }

    private SalesOrderItem createItem(
            SalesOrder order,
            OrderItemRequest request
    ) {
        if (request.itemType() == OrderItemType.PLAN) {
            if (request.planId() == null
                    || request.productId() != null) {
                throw new ConflictException(
                        "invalid_plan_item",
                        "Dữ liệu gói tập trong đơn hàng không hợp lệ"
                );
            }

            if (request.quantity() != 1) {
                throw new ConflictException(
                        "plan_quantity_invalid",
                        "Số lượng gói tập phải bằng 1"
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
                        "Gói tập không hoạt động"
                );
            }

            if (!plan.getBranchId().equals(order.getBranchId())) {
                throw new ConflictException(
                        "plan_branch_mismatch",
                        "Gói tập không thuộc chi nhánh của đơn hàng"
                );
            }

            BigDecimal price = MoneyUtil.normalize(
                    plan.getPrice()
            );

            return SalesOrderItem.builder()
                    .orderId(order.getId())
                    .itemType(OrderItemType.PLAN)
                    .planId(plan.getId())
                    .productId(null)
                    .nameSnapshot(plan.getName())
                    .unitPrice(price)
                    .quantity(1)
                    .lineTotal(price)
                    .build();
        }

        if (request.productId() == null
                || request.planId() != null) {
            throw new ConflictException(
                    "invalid_product_item",
                    "Dữ liệu sản phẩm trong đơn hàng không hợp lệ"
            );
        }

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() -> new NotFoundException(
                        "product_not_found",
                        "Không tìm thấy sản phẩm"
                ));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ConflictException(
                    "product_inactive",
                    "Sản phẩm không hoạt động"
            );
        }

        BigDecimal lineTotal = MoneyUtil.multiply(
                product.getPrice(),
                request.quantity()
        );

        return SalesOrderItem.builder()
                .orderId(order.getId())
                .itemType(OrderItemType.PRODUCT)
                .planId(null)
                .productId(product.getId())
                .nameSnapshot(product.getName())
                .unitPrice(MoneyUtil.normalize(product.getPrice()))
                .quantity(request.quantity())
                .lineTotal(lineTotal)
                .build();
    }

    private void requireScope(
            AppPrincipal principal,
            SalesOrder order
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    order.getBranchId()
            );
            return;
        }

        if (principal.getMemberId() == null
                || order.getMemberId() == null
                || !principal.getMemberId().equals(
                order.getMemberId()
        )) {
            throw new ForbiddenException(
                    "order_out_of_scope",
                    "Bạn không có quyền truy cập đơn hàng này"
            );
        }
    }

    private OrderResponse toResponse(SalesOrder order) {
        List<OrderItemResponse> items = itemRepository
                .findAllByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getItemType(),
                        item.getPlanId(),
                        item.getProductId(),
                        item.getNameSnapshot(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getBranchId(),
                order.getMemberId(),
                order.getStatus(),
                order.getSubtotal(),
                order.getTotal(),
                order.getCreatedByUserId(),
                order.getCreatedAtUtc(),
                order.getPaidAtUtc(),
                items
        );
    }
}