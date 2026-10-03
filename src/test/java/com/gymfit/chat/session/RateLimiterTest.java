package com.gymfit.chat.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private RateLimiter limiter;

    private static final Long USER =
            1L;

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-03T03:00:00Z"
            );

    @BeforeEach
    void setUp() {
        limiter =
                new RateLimiter();

        limiter.clearAll();

        limiter.setLimit(
                3
        );
    }

    @Test
    @DisplayName("Cho phép tới giới hạn, chặn lượt vượt")
    void blocksAfterLimit() {

        assertTrue(
                limiter.tryAcquire(
                        USER,
                        NOW
                ),
                "Lượt 1"
        );

        assertTrue(
                limiter.tryAcquire(
                        USER,
                        NOW.plusSeconds(1)
                ),
                "Lượt 2"
        );

        assertTrue(
                limiter.tryAcquire(
                        USER,
                        NOW.plusSeconds(2)
                ),
                "Lượt 3"
        );

        assertFalse(
                limiter.tryAcquire(
                        USER,
                        NOW.plusSeconds(3)
                ),
                "Lượt 4 phải bị chặn"
        );
    }

    @Test
    @DisplayName("Khác user thì không ảnh hưởng")
    void separateUsers() {

        for (int i = 0; i < 3; i++) {
            assertTrue(
                    limiter.tryAcquire(
                            USER,
                            NOW
                    )
            );
        }

        assertFalse(
                limiter.tryAcquire(
                        USER,
                        NOW
                )
        );

        assertTrue(
                limiter.tryAcquire(
                        2L,
                        NOW
                ),
                "User khác vẫn gửi được"
        );
    }

    @Test
    @DisplayName("Cửa sổ trượt: quá 60 giây thì được gửi lại")
    void slidingWindow() {

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(
                    USER,
                    NOW
            );
        }

        assertFalse(
                limiter.tryAcquire(
                        USER,
                        NOW.plusSeconds(10)
                )
        );

        // Sau 60s, lượt cũ trôi khỏi cửa sổ
        assertTrue(
                limiter.tryAcquire(
                        USER,
                        NOW.plusSeconds(61)
                ),
                "Sau 60 giây phải gửi lại được"
        );
    }

    @Test
    @DisplayName("used() đếm số lượt trong cửa sổ")
    void usedCount() {

        assertEquals(
                0,
                limiter.used(
                        USER,
                        NOW
                )
        );

        limiter.tryAcquire(
                USER,
                NOW
        );

        limiter.tryAcquire(
                USER,
                NOW.plusSeconds(1)
        );

        assertEquals(
                2,
                limiter.used(
                        USER,
                        NOW.plusSeconds(2)
                )
        );

        assertEquals(
                0,
                limiter.used(
                        USER,
                        NOW.plusSeconds(120)
                )
        );
    }

    @Test
    @DisplayName("retryAfterSeconds cho biết cần chờ bao lâu")
    void retryAfter() {

        assertEquals(
                0,
                limiter.retryAfterSeconds(
                        USER,
                        NOW
                )
        );

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(
                    USER,
                    NOW
            );
        }

        assertTrue(
                limiter.retryAfterSeconds(
                        USER,
                        NOW
                ) > 0
        );
    }

    @Test
    @DisplayName("userId null thì không giới hạn")
    void nullUser() {

        for (int i = 0; i < 100; i++) {
            assertTrue(
                    limiter.tryAcquire(
                            null,
                            NOW
                    )
            );
        }
    }

    @Test
    @DisplayName("clear() xoá bộ nhớ của một user")
    void clear() {

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(
                    USER,
                    NOW
            );
        }

        limiter.clear(
                USER
        );

        assertTrue(
                limiter.tryAcquire(
                        USER,
                        NOW
                )
        );
    }

    @Test
    @DisplayName("Bộ nhớ bị giới hạn: không phình vô hạn theo thời gian")
    void boundedMemory() {

        // 1000 lượt trong cùng một giây
        for (int i = 0; i < 1000; i++) {
            limiter.tryAcquire(
                    USER,
                    NOW
            );
        }

        assertTrue(
                limiter.used(
                        USER,
                        NOW
                ) <= 60,
                "Cửa sổ phải được cắt bớt"
        );
    }

    @Test
    @DisplayName("Cửa sổ trượt đúng 60 giây")
    void exactWindowBoundary() {

        limiter.tryAcquire(
                USER,
                NOW
        );

        assertEquals(
                1,
                limiter.used(
                        USER,
                        NOW.plusSeconds(59)
                )
        );

        assertEquals(
                0,
                limiter.used(
                        USER,
                        NOW.plus(
                                60,
                                ChronoUnit.SECONDS
                        )
                )
        );
    }

}