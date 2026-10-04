package com.gymfit.invoice;

import com.gymfit.branch.BranchRepository;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.member.MemberRepository;
import com.gymfit.order.OrderStatus;
import com.gymfit.order.SalesOrder;
import com.gymfit.order.SalesOrderItemRepository;
import com.gymfit.order.SalesOrderRepository;
import com.gymfit.payment.PaymentRepository;
import com.gymfit.user.AppUser;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Luồng xuất hóa đơn – các nhánh rào chắn (thuần Mockito, không cần DB).
 */
class InvoiceServiceTest {

    private SalesOrderRepository orderRepository;
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(SalesOrderRepository.class);

        invoiceService = new InvoiceService(
                orderRepository,
                mock(SalesOrderItemRepository.class),
                mock(PaymentRepository.class),
                mock(BranchRepository.class),
                mock(MemberRepository.class)
        );
    }

    @Test
    @DisplayName("Đơn chưa thanh toán -> 409 invoice_order_not_paid")
    void khongXuatHoaDonChoDonChuaThanhToan() {

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(don(OrderStatus.PENDING_PAYMENT)));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> invoiceService.getInvoice(admin(), 1L)
        );

        assertEquals("invoice_order_not_paid", exception.getCode());
    }

    @Test
    @DisplayName("Hội viên không được xem hóa đơn của hội viên khác -> 403 invoice_out_of_scope")
    void hoiVienNgoaiPhamViBiChan() {

        SalesOrder donCuaHoiVien99 = SalesOrder.builder()
                .id(3L)
                .orderCode("ORD_KHAC")
                .branchId(2L)
                .memberId(99L)
                .status(OrderStatus.PAID)
                .build();

        when(orderRepository.findById(3L))
                .thenReturn(Optional.of(donCuaHoiVien99));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> invoiceService.getInvoice(hoiVien(1L), 3L)
        );

        assertEquals("invoice_out_of_scope", exception.getCode());
    }

    @Test
    @DisplayName("Quản lý chi nhánh không được xem hóa đơn ngoài chi nhánh -> 403")
    void quanLyNgoaiChiNhachBiChan() {

        SalesOrder donChiNhanh2 = SalesOrder.builder()
                .id(4L)
                .orderCode("ORD_CN2")
                .branchId(2L)
                .memberId(null)
                .status(OrderStatus.PAID)
                .build();

        when(orderRepository.findById(4L))
                .thenReturn(Optional.of(donChiNhanh2));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> invoiceService.getInvoice(quanLy(1L), 4L)
        );

        assertEquals("invoice_out_of_scope", exception.getCode());
    }

    @Test
    @DisplayName("Không tìm thấy đơn -> 404 order_not_found")
    void khongTimThayDon() {
        when(orderRepository.findById(404L))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                com.gymfit.common.error.NotFoundException.class,
                () -> invoiceService.getInvoice(admin(), 404L)
        );

        assertEquals("order_not_found", exception.getCode());
    }

    // ------------------------------------------------------------------

    private SalesOrder don(OrderStatus status) {
        return SalesOrder.builder()
                .id(1L)
                .orderCode("ORD_TEST")
                .branchId(1L)
                .memberId(1L)
                .status(status)
                .build();
    }

    private AppPrincipal admin() {
        return principal(100L, RoleCode.ADMIN, null, null);
    }

    private AppPrincipal quanLy(Long branchId) {
        return principal(101L, RoleCode.BRANCH_MANAGER, branchId, null);
    }

    private AppPrincipal hoiVien(Long memberId) {
        return principal(102L, RoleCode.MEMBER, null, memberId);
    }

    private AppPrincipal principal(
            Long userId,
            RoleCode role,
            Long branchId,
            Long memberId
    ) {
        return new AppPrincipal(
                AppUser.builder()
                        .id(userId)
                        .fullName("Test")
                        .email("test@gymfit.local")
                        .passwordHash("hash")
                        .roleCode(role)
                        .status(UserStatus.ACTIVE)
                        .branchId(branchId)
                        .memberId(memberId)
                        .build()
        );
    }
}
