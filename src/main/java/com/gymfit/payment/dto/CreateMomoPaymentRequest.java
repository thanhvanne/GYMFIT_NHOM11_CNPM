package com.gymfit.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMomoPaymentRequest(
        @NotNull
        Long orderId,

        @NotBlank
        @Size(max = 100)
        String idempotencyKey
) {
}