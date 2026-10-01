package com.gymfit.branch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateOperatingHoursRequest(
        @NotEmpty
        List<@Valid OperatingHourItemRequest> hours
) {
}