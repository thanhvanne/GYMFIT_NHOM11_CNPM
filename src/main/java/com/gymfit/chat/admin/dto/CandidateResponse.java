package com.gymfit.chat.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một câu hỏi chưa được bot trả lời chắc chắn, chờ admin gán nhãn.
 *
 * @param id              mã ứng viên
 * @param text            câu người dùng gõ (đã qua {@code PrivacyGuard})
 * @param predictedIntent nhãn mô hình dự đoán
 * @param confidence      độ tin cậy mô hình
 * @param label           nhãn admin đã gán ({@code null} khi chưa gán)
 * @param status          {@code PENDING | LABELED | REJECTED}
 * @param createdAtUtc    thời điểm ghi nhận
 * @param labeledAtUtc    thời điểm admin gán nhãn
 */
public record CandidateResponse(

        Long id,

        String text,

        String predictedIntent,

        BigDecimal confidence,

        String label,

        String status,

        Instant createdAtUtc,

        Instant labeledAtUtc
) {
}
