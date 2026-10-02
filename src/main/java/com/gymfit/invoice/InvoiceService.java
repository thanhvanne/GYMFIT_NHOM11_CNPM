package com.gymfit.invoice;

import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.invoice.dto.InvoiceData;
import com.gymfit.invoice.dto.InvoiceItemResponse;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.order.OrderStatus;
import com.gymfit.order.SalesOrder;
import com.gymfit.order.SalesOrderItemRepository;
import com.gymfit.order.SalesOrderRepository;
import com.gymfit.payment.Payment;
import com.gymfit.payment.PaymentRepository;
import com.gymfit.payment.PaymentStatus;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final BranchRepository branchRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public InvoiceData getInvoice(
            AppPrincipal principal,
            Long orderId
    ) {
        SalesOrder order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new NotFoundException(
                        "order_not_found",
                        "Không tìm thấy đơn hàng"
                ));

        requireScope(principal, order);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException(
                    "invoice_order_not_paid",
                    "Chỉ có thể xuất hóa đơn cho đơn hàng đã thanh toán"
            );
        }

        Branch branch = branchRepository
                .findById(order.getBranchId())
                .orElseThrow(() -> new NotFoundException(
                        "branch_not_found",
                        "Không tìm thấy chi nhánh"
                ));

        Member member = null;

        if (order.getMemberId() != null) {
            member = memberRepository
                    .findById(order.getMemberId())
                    .orElse(null);
        }

        Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByPaidAtUtcDesc(
                        order.getId(),
                        PaymentStatus.SUCCEEDED
                )
                .orElseThrow(() -> new NotFoundException(
                        "successful_payment_not_found",
                        "Không tìm thấy thanh toán thành công của đơn hàng"
                ));

        var items = itemRepository
                .findAllByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(item -> new InvoiceItemResponse(
                        item.getNameSnapshot(),
                        item.getItemType(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();

        return new InvoiceData(
                order.getId(),
                order.getOrderCode(),
                branch.getName(),
                branch.getAddress(),
                branch.getPhone(),
                member == null
                        ? "Khách lẻ"
                        : member.getFullName(),
                member == null
                        ? null
                        : member.getMemberCode(),
                payment.getPaymentCode(),
                payment.getMethod().name(),
                payment.getProviderReference(),
                order.getPaidAtUtc(),
                order.getSubtotal(),
                order.getTotal(),
                items
        );
    }

    private void requireScope(
            AppPrincipal principal,
            SalesOrder order
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            if (principal.getBranchId() == null
                    || !principal.getBranchId()
                    .equals(order.getBranchId())) {
                throw new ForbiddenException(
                        "invoice_out_of_scope",
                        "Bạn không có quyền xem hóa đơn này"
                );
            }

            return;
        }

        if (principal.getRole() == RoleCode.MEMBER) {
            if (principal.getMemberId() == null
                    || order.getMemberId() == null
                    || !principal.getMemberId()
                    .equals(order.getMemberId())) {
                throw new ForbiddenException(
                        "invoice_out_of_scope",
                        "Bạn không có quyền xem hóa đơn này"
                );
            }

            return;
        }

        throw new ForbiddenException(
                "invoice_forbidden",
                "Bạn không có quyền xem hóa đơn"
        );
    }
}