package com.gymfit.chat.dto;

import java.time.Instant;

public record ChatResponse(
        String message,
        Instant createdAtUtc
) {
}