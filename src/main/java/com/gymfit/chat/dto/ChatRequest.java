package com.gymfit.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * Yêu cầu gửi lên {@code POST /api/v1/chat}.
 * <p>{@code message} và {@code payload} không được cùng rỗng — kiểm tra thủ công
 * trong service vì {@code @NotBlank} không áp dụng được khi payload thay thế.
 *
 * @param message   câu người dùng gõ (có thể null nếu gửi payload)
 * @param sessionId phiên hiện tại (null = tạo mới)
 * @param payload   {@code TEXT:<s>} | {@code CONFIRM:<uuid>} | {@code CANCEL:<uuid>}
 */
public record ChatRequest(

        @Size(
                max = 2000,
                message = "Tin nhắn không được vượt quá 2000 ký tự"
        )
        String message,

        @Size(
                max = 64,
                message = "Mã phiên không được vượt quá 64 ký tự"
        )
        String sessionId,

        @Size(
                max = 100,
                message = "Payload không được vượt quá 100 ký tự"
        )
        String payload
) {

    public boolean isEmpty() {
        return isBlank(message)
                && isBlank(payload);

    }

    private static boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }

    public String trimmedMessage() {
        return message == null
                ? null
                : message.trim();
    }

    public String trimmedPayload() {
        return payload == null
                ? null
                : payload.trim();
    }

    public String trimmedSessionId() {
        return sessionId == null
                ? null
                : sessionId.trim();
    }
}