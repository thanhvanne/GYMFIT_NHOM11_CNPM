package com.gymfit.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Đánh giá 👍/👎 cho một tin nhắn của bot.
 */
public record FeedbackRequest(

        @NotNull(message = "Thiếu mã tin nhắn")
        Long messageId,

        @NotNull(message = "Thiếu đánh giá")
        @Pattern(
                regexp = "UP|DOWN",
                message = "Đánh giá phải là UP hoặc DOWN"
        )
        String feedback
) {
}