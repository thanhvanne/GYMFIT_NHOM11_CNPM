package com.gymfit.payment;

import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.CodeGenerator;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.BranchStatus;
import com.gymfit.inventory.InventoryService;
import com.gymfit.membership.MembershipService;
import com.gymfit.order.*;
import com.gymfit.payment.dto.CreateMomoPaymentRequest;
import com.gymfit.payment.dto.PaymentResponse;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymfit.audit.AuditService;
import java.util.Map;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final MomoPaymentProvider momoPaymentProvider;
    private final InventoryService inventoryService;
    private final MembershipService membershipService;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;
    private final BranchRepository branchRepository;

    @Transactional
    public PaymentResponse createMomo(
            AppPrincipal principal,
            CreateMomoPaymentRequest request
    ) {
        SalesOrder order = orderRepository
                .findById(request.orderId())
                .orElseThrow(() -> new NotFoundException(
                        "order_not_found",
                        "Không tìm thấy đơn hàng"
                ));

        requireStaffScope(principal, order);

        String key = request.idempotencyKey().trim();

        Payment existing = paymentRepository
                .findByIdempotencyKey(key)
                .orElse(null);

        if (existing != null) {
            if (!existing.getOrderId().equals(order.getId())) {
                throw new ConflictException(
                        "idempotency_key_conflict",
                        "Idempotency key đã thuộc đơn hàng khác"
                );
            }

            return toResponse(existing);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(
                    "order_not_payable",
                    "Đơn hàng không ở trạng thái chờ thanh toán"
            );
        }

        Payment payment = Payment.builder()
                .paymentCode(CodeGenerator.generate("PAY"))
                .orderId(order.getId())
                .method(PaymentMethod.MOMO)
                .provider(PaymentProviderCode.MOMO_SIMULATOR)
                .status(PaymentStatus.PENDING)
                .amount(order.getTotal())
                .idempotencyKey(key)
                .providerReference(null)
                .createdAtUtc(TimeUtil.now())
                .paidAtUtc(null)
                .build();

        Payment saved = paymentRepository.save(payment);

        saved.setProviderReference(
                momoPaymentProvider.createReference(saved)
        );

        return toResponse(paymentRepository.save(saved));
    }

    @Transactional
    public PaymentResponse simulateSuccess(
            AppPrincipal principal,
            Long paymentId
    ) {
        Payment payment = requirePaymentForUpdate(paymentId);

        SalesOrder order = orderRepository
                .findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new NotFoundException(
                        "order_not_found",
                        "Không tìm thấy đơn hàng"
                ));

        requireStaffScope(principal, order);

        Branch branch = branchRepository
                .findById(order.getBranchId())
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

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return toResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ConflictException(
                    "payment_not_pending",
                    "Thanh toán không còn ở trạng thái chờ"
            );
        }

        if (order.getStatus() == OrderStatus.PAID) {
            throw new ConflictException(
                    "order_already_paid",
                    "Đơn hàng đã được thanh toán"
            );
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(
                    "order_not_payable",
                    "Đơn hàng không thể thanh toán"
            );
        }

        List<SalesOrderItem> items =
                itemRepository.findAllByOrderIdOrderByIdAsc(
                        order.getId()
                );

        if (items.isEmpty()) {
            throw new ConflictException(
                    "order_empty",
                    "Đơn hàng không có sản phẩm"
            );
        }

        if (principal.getRole() == RoleCode.MEMBER) {
            boolean validMemberPurchase =
                    items.size() == 1
                            && items.get(0).getItemType()
                            == OrderItemType.PLAN;

            if (!validMemberPurchase) {
                throw new ForbiddenException(
                        "member_payment_type_forbidden",
                        "Hội viên chỉ được thanh toán đơn mua gói tập"
                );
            }
        }

        for (SalesOrderItem item : items) {
            if (item.getItemType() == OrderItemType.PRODUCT) {
                inventoryService.deductForSale(
                        order.getBranchId(),
                        item.getProductId(),
                        item.getQuantity(),
                        principal.getUserId(),
                        "Bán hàng " + order.getOrderCode()
                );
            }
        }

        for (SalesOrderItem item : items) {
            if (item.getItemType() == OrderItemType.PLAN) {
                if (order.getMemberId() == null) {
                    throw new ConflictException(
                            "plan_member_required",
                            "Đơn mua gói tập phải có hội viên"
                    );
                }

                membershipService.activateFromPaidOrder(
                        order.getMemberId(),
                        item.getPlanId(),
                        order.getId(),
                        principal.getUserId()
                );
            }
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAtUtc(TimeUtil.now());

        order.setStatus(OrderStatus.PAID);
        order.setPaidAtUtc(payment.getPaidAtUtc());

        orderRepository.save(order);

        auditService.record(
                principal.getUserId(),
                "PAYMENT_SUCCEEDED",
                "PAYMENT",
                payment.getId(),
                order.getBranchId(),
                Map.of(
                        "orderId", order.getId(),
                        "amount", payment.getAmount().toPlainString(),
                        "provider", payment.getProvider().name()
                )
        );

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse simulateFailure(
            AppPrincipal principal,
            Long paymentId
    ) {
        Payment payment = requirePaymentForUpdate(paymentId);

        SalesOrder order = orderRepository
                .findById(payment.getOrderId())
                .orElseThrow(() -> new NotFoundException(
                        "order_not_found",
                        "Không tìm thấy đơn hàng"
                ));

        requireStaffScope(principal, order);

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return toResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ConflictException(
                    "payment_not_pending",
                    "Thanh toán không còn ở trạng thái chờ"
            );
        }

        payment.setStatus(PaymentStatus.FAILED);

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(
            AppPrincipal principal,
            Long paymentId
    ) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new NotFoundException(
                        "payment_not_found",
                        "Không tìm thấy thanh toán"
                ));

        SalesOrder order = orderRepository
                .findById(payment.getOrderId())
                .orElseThrow(() -> new NotFoundException(
                        "order_not_found",
                        "Không tìm thấy đơn hàng"
                ));

        requireReadScope(principal, order);

        return toResponse(payment);
    }

    private Payment requirePaymentForUpdate(Long id) {
        return paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(
                        "payment_not_found",
                        "Không tìm thấy thanh toán"
                ));
    }

    private void requireStaffScope(
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

        if (principal.getRole() == RoleCode.MEMBER) {
            if (principal.getMemberId() == null
                    || order.getMemberId() == null
                    || !principal.getMemberId()
                    .equals(order.getMemberId())) {

                throw new ForbiddenException(
                        "payment_out_of_scope",
                        "Bạn không có quyền thanh toán đơn hàng này"
                );
            }

            return;
        }

        throw new ForbiddenException(
                "payment_operation_forbidden",
                "Bạn không có quyền thực hiện thanh toán"
        );
    }

    private void requireReadScope(
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
                    "payment_out_of_scope",
                    "Bạn không có quyền xem thanh toán này"
            );
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getProviderReference(),
                payment.getCreatedAtUtc(),
                payment.getPaidAtUtc()
        );
    }
}