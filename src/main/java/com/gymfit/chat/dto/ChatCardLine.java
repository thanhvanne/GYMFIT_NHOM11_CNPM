package com.gymfit.chat.dto;

/**
 * Một dòng trong thẻ hiển thị (CONFIRM / LIST).
 */
public record ChatCardLine(
        String label,
        String value
) {

    public static ChatCardLine of(
            String label,
            Object value
    ) {
        return new ChatCardLine(
                label,
                value == null
                        ? ""
                        : String.valueOf(value)
        );
    }
}