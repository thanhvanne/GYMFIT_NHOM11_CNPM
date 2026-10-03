package com.gymfit.chat.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyGuardTest {

    @Test
    @DisplayName("Che số điện thoại")
    void masksPhone() {
        assertEquals(
                "SĐT của tôi là [SĐT] nhé",
                PrivacyGuard.mask(
                        "SĐT của tôi là 0912345678 nhé"
                )
        );
    }

    @Test
    @DisplayName("Che email")
    void masksEmail() {
        assertEquals(
                "email [email] đó",
                PrivacyGuard.mask(
                        "email minh@gmail.com đó"
                )
        );
    }

    @Test
    @DisplayName("Che JWT / bearer token")
    void masksToken() {
        String text =
                "token là eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123";

        assertEquals(
                "token là [token]",
                PrivacyGuard.mask(text)
        );
    }

    @Test
    @DisplayName("Cắt còn tối đa 2000 ký tự")
    void truncates() {
        String long2000 =
                "a".repeat(
                        2500
                );

        assertEquals(
                2000,
                PrivacyGuard.mask(long2000)
                        .length()
        );
    }

    @Test
    @DisplayName("Null / rỗng → chuỗi rỗng")
    void nullSafe() {
        assertEquals(
                "",
                PrivacyGuard.mask(null)
        );

        assertEquals(
                "",
                PrivacyGuard.mask("   ")
        );
    }

    @Test
    @DisplayName("Câu thường không bị đổi")
    void keepsNormalText() {
        String text =
                "cho tôi xem lịch tập ngày mai";

        assertEquals(
                text,
                PrivacyGuard.mask(text)
        );
    }

    @Test
    @DisplayName("Không che nhầm mã booking")
    void keepsBookingCode() {

        String text =
                "hủy lịch BOOK_0123456789ABCDEF";

        assertTrue(
                PrivacyGuard.mask(text)
                        .contains("BOOK_0123456789ABCDEF")
        );
    }

    @Test
    @DisplayName("Che cả khi có cả SĐT lẫn email trong một câu")
    void masksBoth() {

        String masked =
                PrivacyGuard.mask(
                        "gọi 0901234567 hoặc gửi test@example.com"
                );

        assertFalse(
                masked.contains("0901234567")
        );

        assertFalse(
                masked.contains("test@example.com")
        );

        assertTrue(
                masked.contains("[SĐT]")
        );

        assertTrue(
                masked.contains("[email]")
        );
    }

}