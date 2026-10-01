package com.gymfit.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        CurrentUserResponse user
) {
}