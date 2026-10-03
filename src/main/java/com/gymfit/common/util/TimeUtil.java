package com.gymfit.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TimeUtil {

    public static final ZoneId VIETNAM =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private TimeUtil() {
    }

    public static Instant now() {
        return Instant.now();
    }

    /** Ngày theo múi giờ nghiệp vụ (Asia/Ho_Chi_Minh). */
    public static LocalDate today() {
        return LocalDate.now(VIETNAM);
    }
}