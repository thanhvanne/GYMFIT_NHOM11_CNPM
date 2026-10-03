package com.gymfit.chat.dto;

import java.time.Instant;
import java.util.List;

/**
 * Câu trả lời của chatbot.
 *
 * @param sessionId   phiên hiện tại (client giữ để gửi kèm lượt sau)
 * @param message     nội dung trả lời
 * @param intent      intent đã xử lý
 * @param confidence  độ tin cậy
 * @param suggestions gợi ý nhanh
 * @param card        thẻ xác nhận / danh sách (có thể null)
 * @param messageId   id tin nhắn bot — dùng cho nút 👍/👎
 * @param createdAtUtc thời điểm tạo
 */
public record ChatResponse(
        String sessionId,
        String message,
        String intent,
        Double confidence,
        List<ChatSuggestion> suggestions,
        ChatCard card,
        Long messageId,
        Instant createdAtUtc
) {

    public static ChatResponse of(
            String sessionId,
            String message,
            String intent,
            Double confidence
    ) {
        return new ChatResponse(
                sessionId,
                message,
                intent,
                confidence,
                List.of(),
                null,
                null,
                com.gymfit.common.util.TimeUtil.now()
        );
    }

    public ChatResponse withMessageId(
            Long id
    ) {
        return new ChatResponse(
                sessionId,
                message,
                intent,
                confidence,
                suggestions,
                card,
                id,
                createdAtUtc
        );
    }

    public ChatResponse withSuggestions(
            List<ChatSuggestion> values
    ) {
        return new ChatResponse(
                sessionId,
                message,
                intent,
                confidence,
                values == null ? List.of() : List.copyOf(values),
                card,
                messageId,
                createdAtUtc
        );
    }

    public ChatResponse withCard(
            ChatCard value
    ) {
        return new ChatResponse(
                sessionId,
                message,
                intent,
                confidence,
                suggestions,
                value,
                messageId,
                createdAtUtc
        );
    }
}