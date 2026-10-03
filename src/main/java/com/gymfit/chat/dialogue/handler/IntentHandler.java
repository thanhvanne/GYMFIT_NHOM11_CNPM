package com.gymfit.chat.dialogue.handler;

import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Xử lý một nhóm intent.
 * <p>Mỗi handler bọc {@link ApiException} để trả câu tiếng Việt của service,
 * không bao giờ lộ stack trace cho người dùng.
 */
public interface IntentHandler {

    org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(
                    IntentHandler.class
            );

    Set<Intent> supports();

    ChatResponse handle(
            HandlerContext context
    );

    default ChatResponse fail(
            HandlerContext context,
            ApiException exception
    ) {
        LOG.warn(
                "{} bị lỗi: {} - {}",
                supports(),
                exception.getCode(),
                exception.getMessage()
        );

        return ChatResponse.of(
                null,
                exception.getMessage(),
                context.intent() == null
                        ? null
                        : context.intent()
                                .name(),
                null
        );
    }

    static ChatResponse empty(
            HandlerContext context
    ) {
        return ChatResponse.of(
                null,
                "Hiện tại mình chưa có dữ liệu cho phần này.",
                context.intent() == null
                        ? null
                        : context.intent()
                                .name(),
                null
        );
    }

    static ChatResponse message(
            HandlerContext context,
            String text
    ) {
        return ChatResponse.of(
                null,
                text,
                context.intent() == null
                        ? null
                        : context.intent()
                                .name(),
                null
        );
    }

    static ChatResponse message(
            HandlerContext context,
            String text,
            java.util.List<com.gymfit.chat.dto.ChatSuggestion> suggestions
    ) {
        return ChatResponse.of(
                        null,
                        text,
                        context.intent() == null
                                ? null
                                : context.intent()
                                        .name(),
                        null
                )
                .withSuggestions(
                        suggestions
                );
    }

    static ChatResponse now() {
        return ChatResponse.of(
                null,
                "",
                null,
                null
        );
    }

    static java.time.Instant nowInstant() {
        return TimeUtil.now();
    }
}