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
 * @param slots     slot đã điền trong hội thoại
 */
public record HandlerContext(
        AppPrincipal principal,
        Intent intent,
        Entities entities,
        NormalizedText text,
        String raw,
        LocalDate today,
        java.util.Map<String, String> slots
) {

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
                values
        );
    }
}