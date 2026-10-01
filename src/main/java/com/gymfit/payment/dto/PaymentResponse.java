package com.gymfit.payment.dto;

import com.gymfit.payment.PaymentMethod;
import com.gymfit.payment.PaymentProviderCode;
import com.gymfit.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String paymentCode,
        Long orderId,
        PaymentMethod method,
        PaymentProviderCode provider,
        PaymentStatus status,
        BigDecimal amount,
        String providerReference,
        Instant createdAtUtc,
        Instant paidAtUtc
) {
}