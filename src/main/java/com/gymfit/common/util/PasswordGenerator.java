package com.gymfit.common.util;

import java.security.SecureRandom;

/**
 * Sinh mật khẩu tạm ngẫu nhiên cho tài khoản hội viên (D2).
 *
 * <p>10 ký tự, luôn có ≥1 chữ hoa, ≥1 chữ thường, ≥1 chữ số.
 * Bỏ các ký tự dễ nhầm (0/O, 1/l/I) để nhân viên đọc lại mật khẩu cho hội viên
 * không bị sai. Trộn Fisher–Yates bằng {@link SecureRandom}.
 *
 * <p>Mật khẩu thô chỉ được giữ trong bộ nhớ và trả về qua các endpoint cấp tài
 * khoản – <b>không</b> ghi log/audit/DB.
 */
public final class PasswordGenerator {

    /**
     * Số ký tự mật khẩu tạm.
     */
    public static final int LENGTH = 10;

    /**
     * Chữ hoa: bỏ I và O.
     */
    private static final String UPPER =
            "ABCDEFGHJKLMNPQRSTUVWXYZ";

    /**
     * Chữ thường: bỏ l và o.
     */
    private static final String LOWER =
            "abcdefghijkmnpqrstuvwxyz";

    /**
     * Chữ số: bỏ 0 và 1.
     */
    private static final String DIGIT =
            "23456789";

    private static final String ALL =
            UPPER + LOWER + DIGIT;

    private static final SecureRandom RND =
            new SecureRandom();

    private PasswordGenerator() {
    }

    /**
     * Sinh một mật khẩu đủ điều kiện.
     */
    public static String generate() {

        StringBuilder builder =
                new StringBuilder(LENGTH);

        // Bảo đảm đủ 3 nhóm ký tự trước
        builder.append(
                pick(UPPER)
        );
        builder.append(
                pick(LOWER)
        );
        builder.append(
                pick(DIGIT)
        );

        while (builder.length() < LENGTH) {
            builder.append(
                    pick(ALL)
            );
        }

        return shuffle(
                builder.toString()
        );
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(
                RND.nextInt(
                        alphabet.length()
                )
        );
    }

    /**
     * Trộn Fisher–Yates để nhóm ký tự không nằm cố định ở đầu chuỗi.
     */
    private static String shuffle(String value) {

        char[] chars =
                value.toCharArray();

        for (int i = chars.length - 1; i > 0; i--) {

            int j = RND.nextInt(i + 1);

            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }
}
