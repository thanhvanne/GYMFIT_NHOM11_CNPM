package com.gymfit.invoice.dto;

import com.gymfit.order.OrderItemType;

import java.math.BigDecimal;

public record InvoiceItemResponse(
        String name,
        OrderItemType itemType,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {
}