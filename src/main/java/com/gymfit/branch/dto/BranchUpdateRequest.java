package com.gymfit.branch.dto;

import com.gymfit.branch.BranchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BranchUpdateRequest(
        @NotBlank
        @Size(max = 20)
        String code,

        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Size(max = 255)
        String address,

        @Size(max = 20)
        String phone,

        @NotNull
        BranchStatus status,

        @NotBlank
        @Size(max = 60)
        String timezone
) {
}