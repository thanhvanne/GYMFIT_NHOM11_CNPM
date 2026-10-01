package com.gymfit.checkin.dto;

import java.time.Instant;

public record QrResponse(
        String token,
        String imageDataUrl,
        Instant expiresAtUtc
) {
}