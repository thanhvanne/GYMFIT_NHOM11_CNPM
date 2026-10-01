package com.gymfit.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
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

        @NotBlank
        @Size(max = 60)
        String timezone
) {
}