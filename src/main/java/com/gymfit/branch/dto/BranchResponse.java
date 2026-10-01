package com.gymfit.branch.dto;

import com.gymfit.branch.BranchStatus;

import java.time.Instant;

public record BranchResponse(
        Long id,
        String code,
        String name,
        String address,
        String phone,
        BranchStatus status,
        String timezone,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}