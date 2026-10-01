package com.gymfit.order.dto;

import com.gymfit.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        Long branchId,
        Long memberId,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal total,
        Long createdByUserId,
        Instant createdAtUtc,
        Instant paidAtUtc,
        List<OrderItemResponse> items
) {
}