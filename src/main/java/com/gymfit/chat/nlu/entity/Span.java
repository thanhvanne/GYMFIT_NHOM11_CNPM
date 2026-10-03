package com.gymfit.chat.nlu.entity;

/**
 * Một cụm từ đã được nhận diện trong {@code plain}, tính theo chỉ số ký tự.
 *
 * @param type loại entity
 * @param start chỉ số bắt đầu (inclusive)
 * @param end   chỉ số kết thúc (exclusive)
 * @param text  nguyên văn cụm từ trong {@code plain}
 */
public record Span(
        EntityType type,
        int start,
        int end,
        String text
) {
}