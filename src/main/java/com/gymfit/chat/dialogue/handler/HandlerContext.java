package com.gymfit.chat.dialogue.handler;

import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlu.NormalizedText;
import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.common.security.AppPrincipal;

import java.time.LocalDate;

/**
 * Ngữ cảnh truyền vào handler.
 *
 * @param principal người đang hỏi (nguồn duy nhất của quyền)
 * @param intent    intent cần xử lý
 * @param entities  entity đã trích
 * @param text      câu đã chuẩn hóa
 * @param raw       câu gõ thô
 * @param today     ngày theo múi giờ nghiệp vụ
 * @param slots     slot đã điền trong hội thoại (map <b>sống</b> của state)
 * @param state     trạng thái hội thoại — handler ghi thẳng {@code awaiting}/
 *                  {@code pending} vào đây (null khi dựng context trong test)
 */
public record HandlerContext(
        AppPrincipal principal,
        Intent intent,
        Entities entities,
        NormalizedText text,
        String raw,
        LocalDate today,
        java.util.Map<String, String> slots,
        com.gymfit.chat.dialogue.ConversationState state
) {

    /** Constructor ngắn cho test — không kèm trạng thái hội thoại. */
    public HandlerContext(
            AppPrincipal principal,
            Intent intent,
            Entities entities,
            NormalizedText text,
            String raw,
            LocalDate today,
            java.util.Map<String, String> slots
    ) {
        this(
                principal,
                intent,
                entities,
                text,
                raw,
                today,
                slots,
                null
        );
    }

    public HandlerContext withSlots(
            java.util.Map<String, String> values
    ) {
        return new HandlerContext(
                principal,
                intent,
                entities,
                text,
                raw,
                today,
                values,
                state
        );
    }

    /**
     * Trạng thái hội thoại; nếu không truyền (test) thì dùng bản tạm
     * để handler vẫn chạy được.
     */
    public com.gymfit.chat.dialogue.ConversationState stateOrNew() {
        return state == null
                ? new com.gymfit.chat.dialogue.ConversationState()
                : state;
    }
}