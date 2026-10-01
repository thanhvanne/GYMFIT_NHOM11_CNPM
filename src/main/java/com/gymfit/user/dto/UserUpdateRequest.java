package com.gymfit.user.dto;

import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @Size(min = 6, max = 72)
        String password,

        @NotNull
        RoleCode role,

        @NotNull
        UserStatus status,

        Long branchId,

        Long memberId
) {
}