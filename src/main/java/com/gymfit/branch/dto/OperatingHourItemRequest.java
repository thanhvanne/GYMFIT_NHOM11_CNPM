package com.gymfit.branch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record OperatingHourItemRequest(
        @NotNull
        @Min(1)
        @Max(7)
        Integer dayOfWeek,

        @NotNull
        LocalTime openTime,

        @NotNull
        LocalTime closeTime
) {
}