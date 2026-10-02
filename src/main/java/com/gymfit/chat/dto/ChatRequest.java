package com.gymfit.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        @NotBlank(message = "Tin nhắn không được để trống")
        @Size(
                max = 2000,
                message = "Tin nhắn không được vượt quá 2000 ký tự"
        )
        String message

) {
}