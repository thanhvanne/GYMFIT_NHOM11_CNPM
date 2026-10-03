package com.gymfit.chat.session;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn số lượt chat mỗi phút theo user (sliding window trong bộ nhớ).
 *
 * <p>Không hỗ trợ HTTP 429 trong {@code GlobalExceptionHandler} → trả
 * {@code false} để lớp trên trả ChatResponse thân thiện.
 */
@Component
public class RateLimiter {

    /** Số cửa sổ giữ lại cho mỗi user (đủ để tính sliding window). */
    private static final int MAX_STAMPS =
            60;

    private final Map<Long, Deque<Instant>> hits =
            new ConcurrentHashMap<>();

    private volatile int limit =
            20;

    public void setLimit(
            int limit
    ) {
        if (limit > 0) {
            this.limit =
                    limit;
        }
    }

    public int limit() {
        return limit;
    }

    /**
     * Ghi nhận một lượt.
     *
     * @return true nếu còn cho phép, false nếu vượt giới hạn
     */
    public boolean tryAcquire(
            Long userId,
            Instant now
    ) {
        if (userId == null) {
            return true;
        }

        Deque<Instant> window =
                hits.computeIfAbsent(
                        userId,
                        key -> new ArrayDeque<>()
                );

        synchronized (window) {

            window.addLast(now);

            while (!window.isEmpty()
                    && !window.peekFirst()
                    .isAfter(
                    now.minusSeconds(60)
            )) {
                window.removeFirst();
            }

            while (window.size() > MAX_STAMPS) {
                window.removeFirst();
            }

            return window.size() <= limit;
        }
    }

    /** Số lượt còn trong cửa sổ hiện tại. */
    public int used(
            Long userId,
            Instant now
    ) {
        Deque<Instant> window =
                hits.get(userId);

        if (window == null) {
            return 0;
        }

        synchronized (window) {

            while (!window.isEmpty()
                    && !window.peekFirst()
                    .isAfter(
                    now.minusSeconds(60)
            )) {
                window.removeFirst();
            }

            return window.size();
        }
    }

    /** Xoá bộ nhớ cho 1 user (dùng khi cần). */
    public void clear(
            Long userId
    ) {
        hits.remove(userId);
    }

    /** Xoá toàn bộ (dùng trong test). */
    public void clearAll() {
        hits.clear();
    }

    /**
     * Số giây cần chờ để gửi được lượt tiếp theo.
     */
    public long retryAfterSeconds(
            Long userId,
            Instant now
    ) {
        Deque<Instant> window =
                hits.get(userId);

        if (window == null
                || window.size() < limit) {
            return 0;
        }

        Instant oldest =
                window.peekFirst();

        if (oldest == null) {
            return 0;
        }

        long wait =
                60 - java.time.Duration.between(
                        oldest,
                        now
                ).getSeconds();

        return Math.max(
                1,
                wait
        );
    }
}