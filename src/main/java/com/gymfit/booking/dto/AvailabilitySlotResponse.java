package com.gymfit.booking.dto;

import java.time.Instant;

public record AvailabilitySlotResponse(
        Instant startsAtUtc,
        Instant endsAtUtc,
        int remainingCapacity
) {
}