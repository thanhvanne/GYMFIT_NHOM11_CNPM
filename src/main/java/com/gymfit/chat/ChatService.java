package com.gymfit.chat;

import com.gymfit.chat.dialogue.DialogueManager;
import com.gymfit.chat.dto.ChatRequest;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.FeedbackRequest;
import com.gymfit.chat.session.ChatSessionService;
import com.gymfit.common.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Cổng vào của chatbot: một lượt hội thoại đi qua {@link DialogueManager},
 * đánh giá 👍/👎 đi thẳng vào kho lưu tin nhắn.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final DialogueManager dialogueManager;

    private final ChatSessionService sessions;

    public ChatResponse chat(
            AppPrincipal principal,
            ChatRequest request
    ) {
        return dialogueManager.handle(
                principal,
                request
        );
    }

    public ChatResponse chat(
            AppPrincipal principal,
            String message
    ) {
        return chat(
                principal,
                new ChatRequest(
                        message,
                        null,
                        null
                )
        );
    }

    /**
     * Ghi 👍/👎. Trả về câu cảm ơn — không lộ việc ghi có thành công hay không
     * (tin không thuộc phiên của người này → giữ im như đã ghi).
     */
    public ChatResponse feedback(
            AppPrincipal principal,
            FeedbackRequest request
    ) {

        if (principal == null
                || principal.getUserId() == null
                || request == null
                || request.messageId() == null
                || request.sessionId() == null
                || request.sessionId()
                .isBlank()) {

            return ChatResponse.of(
                    null,
                    "Cảm ơn bạn đã phản hồi!",
                    null,
                    null
            );
        }

        sessions.feedback(
                request.messageId(),
                request.sessionId(),
                principal.getUserId(),
                request.feedback()
        );

        return ChatResponse.of(
                null,
                "Cảm ơn bạn đã phản hồi! Mình sẽ cải thiện câu trả lời.",
                null,
                null
        );
    }
}
