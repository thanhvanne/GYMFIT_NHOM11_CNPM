package com.gymfit.branch.dto;

import com.gymfit.branch.BranchStatus;
import jakarta.validation.constraints.NotNull;

public record BranchStatusRequest(
        @NotNull BranchStatus status
) {
}