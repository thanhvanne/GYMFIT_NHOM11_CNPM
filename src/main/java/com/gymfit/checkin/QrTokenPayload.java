package com.gymfit.checkin;

public record QrTokenPayload(
        String jti,
        Long memberId
) {
}