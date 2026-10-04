package com.gymfit.common.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Đồng hồ nghiệp vụ (Asia/Ho_Chi_Minh).
 *
 * <p>Mặc định là {@link Clock#system(ZoneId)}. Test có thể ghim thời điểm bằng
 * {@link #useClock(Clock)} để dữ liệu fixture (ngày hiệu lực gói, ca tập…)
 * không phụ thuộc ngày máy chạy — bắt buộc cho bộ kịch bản hội thoại
 * {@code ScenarioRunnerTest} (plan V2, mục 7.1 "Clock cố định").
 *
 * <p><b>Luân phiên giữa các test:</b> mọi test dùng {@link #useClock} phải
 * gọi {@link #resetClock()} trong {@code @AfterEach}, nếu không test khác sẽ
 * thấy giờ giả.
 */
public final class TimeUtil {

    public static final ZoneId VIETNAM =
            ZoneId.of("Asia/Ho_Chi_Minh");

    /** Đồng hồ hệ thống — giá trị trả về khi không ghim test. */
    private static final Clock SYSTEM =
            Clock.system(VIETNAM);

    private static volatile Clock clock =
            SYSTEM;

    private TimeUtil() {
    }

    /** Ghim đồng hồ (chỉ dùng trong test). */
    public static void useClock(
            Clock custom
    ) {
        clock = custom == null
                ? SYSTEM
                : custom;
    }

    /** Trả về đồng hồ hệ thống. */
    public static void resetClock() {
        clock = SYSTEM;
    }

    public static Instant now() {
        return Instant.now(clock);
    }

    /** Ngày theo múi giờ nghiệp vụ (Asia/Ho_Chi_Minh). */
    public static LocalDate today() {
        return LocalDate.now(clock);
    }
}
