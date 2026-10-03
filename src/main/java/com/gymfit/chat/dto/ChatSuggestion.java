package com.gymfit.chat.dto;

/**
 * Gợi ý hiển thị dưới tin nhắn bot.
 * <p>{@code payload} mặc định là {@code TEXT:<label>} — bấm nút tương đương
 * người dùng gõ label.
 */
public record ChatSuggestion(
        String label,
        String payload
) {

    public static ChatSuggestion of(
            String label
    ) {
        return new ChatSuggestion(
                label,
                "TEXT:" + label
        );
    }

    /** Nút gửi sẵn payload (dùng cho CONFIRM/CANCEL và chọn slot). */
    public static ChatSuggestion payload(
            String label,
            String payload
    ) {
        return new ChatSuggestion(
                label,
                payload
        );
    }
}