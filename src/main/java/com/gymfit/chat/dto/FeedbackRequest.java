package com.gymfit.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Đánh giá 👍/👎 cho một tin nhắn của bot.
 *
 * @param sessionId phiên chứa tin nhắn — để không cho đánh giá tin của người khác
 */
public record FeedbackRequest(

        @Size(
                max = 64,
                message = "Mã phiên không được vượt quá 64 ký tự"
        )
        String sessionId,

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
