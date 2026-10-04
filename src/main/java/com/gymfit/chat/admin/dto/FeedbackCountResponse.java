package com.gymfit.chat.admin.dto;

/**
 * Số 👍/👎 của bot theo từng intent.
 *
 * @param intent    intent của câu trả lời bị đánh giá
 * @param up        số 👍
 * @param down      số 👎
 * @param downRate  tỷ lệ 👎 = down / (up + down), làm tròn 4 chữ số
 */
public record FeedbackCountResponse(

        String intent,

        long up,

        long down,

        double downRate
) {
}
