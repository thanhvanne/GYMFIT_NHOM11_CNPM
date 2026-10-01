package com.gymfit.member.dto;

import com.gymfit.member.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MemberUpdateRequest(
        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Size(max = 20)
        String phone,

        @Email
        @Size(max = 150)
        String email,

        @NotNull
        Long homeBranchId,

        LocalDate dateOfBirth,

        @NotNull
        MemberStatus status
) {
}