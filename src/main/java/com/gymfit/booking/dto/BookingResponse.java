package com.gymfit.booking.dto;

import com.gymfit.booking.BookingStatus;
import com.gymfit.branch.ServiceCode;

import java.time.Instant;

public record BookingResponse(
        Long id,
        String bookingCode,
        Long memberId,
        Long membershipId,
        Long branchId,
        ServiceCode serviceCode,
        Long facilityId,
        BookingStatus status,
        Instant startsAtUtc,
        Instant endsAtUtc,
        Instant cancelledAtUtc,
        String cancellationReason,
        Long createdByUserId,
        Instant createdAtUtc
) {
}