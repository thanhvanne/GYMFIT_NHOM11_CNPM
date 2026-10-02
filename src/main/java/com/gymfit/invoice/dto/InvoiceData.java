package com.gymfit.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceData(
        Long orderId,
        String orderCode,
        String branchName,
        String branchAddress,
        String branchPhone,
        String customerName,
        String memberCode,
        String paymentCode,
        String paymentMethod,
        String providerReference,
        Instant paidAtUtc,
        BigDecimal subtotal,
        BigDecimal total,
        List<InvoiceItemResponse> items
) {
}