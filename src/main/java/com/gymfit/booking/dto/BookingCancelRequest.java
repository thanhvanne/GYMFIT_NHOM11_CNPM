package com.gymfit.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingCancelRequest(
        @NotBlank
        @Size(max = 200)
        String reason
) {
}