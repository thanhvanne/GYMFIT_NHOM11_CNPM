package com.gymfit.order.dto;

import com.gymfit.order.OrderItemType;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        OrderItemType itemType,
        Long planId,
        Long productId,
        String name,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {
}