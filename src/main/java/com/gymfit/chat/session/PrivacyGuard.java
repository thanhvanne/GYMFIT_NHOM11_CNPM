package com.gymfit.chat.session;

import java.util.regex.Pattern;

/**
 * Che thông tin cá nhân trước khi lưu vào DB
 * (nội dung chat được giữ lại để cải thiện mô hình).
 */
public final class PrivacyGuard {

    /** Số điện thoại Việt Nam. */
    private static final Pattern PHONE =
            Pattern.compile(
                    "\\b0\\d{9,10}\\b"
            );

    private static final Pattern EMAIL =
            Pattern.compile(
                    "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"
            );

    /** Bearer token / JWT. */
    private static final Pattern JWT =
            Pattern.compile(
                    "\\beyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]*"
            );

    private static final int MAX_LENGTH =
            2000;

    private PrivacyGuard() {
    }

    /**
     * Che SĐT / email / JWT và cắt còn tối đa 2000 ký tự.
     */
    public static String mask(
            String text
    ) {
        if (text == null
                || text.isBlank()) {
            return "";
        }

        String result =
                text;

        result =
                JWT.matcher(result)
                        .replaceAll("[token]");

        result =
                EMAIL.matcher(result)
                        .replaceAll("[email]");

        result =
                PHONE.matcher(result)
                        .replaceAll("[SĐT]");

        if (result.length() > MAX_LENGTH) {
            result =
                    result.substring(
                            0,
                            MAX_LENGTH
                    );
        }

        return result;
    }
}