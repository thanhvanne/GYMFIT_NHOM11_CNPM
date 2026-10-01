package com.gymfit.branch.dto;

import java.time.LocalTime;

public record OperatingHourResponse(
        Long id,
        Long branchId,
        Integer dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime
) {
}