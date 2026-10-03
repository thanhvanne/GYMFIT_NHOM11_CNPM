package com.gymfit.chat.nlu;

/**
 * Kết quả chuẩn hóa văn bản.
 *
 * @param raw  văn bản gốc người dùng gõ (đã trim, giữ nguyên dấu) — dùng để lưu log
 * @param plain văn bản chuẩn hóa: không dấu, chữ thường, một khoảng trắng giữa các từ
 */
public record NormalizedText(
        String raw,
        String plain
) {
}