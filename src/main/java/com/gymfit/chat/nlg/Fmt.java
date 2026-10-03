package com.gymfit.chat.nlg;

import com.gymfit.branch.ServiceCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Định dạng dữ liệu tiếng Việt cho câu trả lời.
 */
public final class Fmt {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy",
                    Locale.ROOT
            );

    private Fmt() {
    }

    /**
     * 1500000 → "1.500.000đ"
     */
    public static String money(
            BigDecimal value
    ) {
        if (value == null) {
            return "0đ";
        }

        BigDecimal rounded =
                value.setScale(
                        0,
                        RoundingMode.HALF_UP
                );

        String digits =
                rounded.toBigInteger()
                        .toString();

        StringBuilder sb =
                new StringBuilder();

        int count =
                0;

        for (int i = digits.length() - 1; i >= 0; i--) {

            sb.append(
                    digits.charAt(i)
            );

            count++;

            if (count % 3 == 0
                    && i > 0) {
                sb.append('.');
            }
        }

        return sb.reverse()
                .append("đ")
                .toString();
    }

    public static String money(
            Long value
    ) {
        return money(
                value == null
                        ? null
                        : BigDecimal.valueOf(value)
        );
    }

    /**
     * 04/10/2026 (Chủ nhật)
     */
    public static String date(
            LocalDate value
    ) {
        if (value == null) {
            return "";
        }

        return value.format(DATE)
                + " ("
                + dayName(
                        value.getDayOfWeek()
                                .getValue()
                )
                + ")";
    }

    /**
     * Chỉ ngày, không kèm thứ.
     */
    public static String shortDate(
            LocalDate value
    ) {
        return value == null
                ? ""
                : value.format(DATE);
    }

    /**
     * 2026-10-04T12:00:00Z theo giờ chi nhánh → "19:00"
     */
    public static String time(
            Instant instant,
            ZoneId zone
    ) {
        if (instant == null) {
            return "";
        }

        return time(
                instant.atZone(
                                zone == null
                                        ? com.gymfit.common.util.TimeUtil.VIETNAM
                                        : zone
                        )
                        .toLocalTime()
        );
    }

    public static String time(
            LocalTime value
    ) {
        if (value == null) {
            return "";
        }

        return String.format(
                Locale.ROOT,
                "%02d:%02d",
                value.getHour(),
                value.getMinute()
        );
    }

    /**
     * Ngày + giờ theo múi giờ chi nhánh: "19:00 04/10/2026"
     */
    public static String dateTime(
            Instant instant,
            ZoneId zone
    ) {
        if (instant == null) {
            return "";
        }

        ZoneId actual =
                zone == null
                        ? com.gymfit.common.util.TimeUtil.VIETNAM
                        : zone;

        var zoned =
                instant.atZone(actual);

        return time(
                zoned.toLocalTime()
        ) + " " + zoned.toLocalDate()
                .format(DATE);
    }

    public static String dateTime(
            LocalDate date,
            LocalTime time
    ) {
        if (date == null) {
            return time(
                    time
            );
        }

        return time(
                time
        ) + " " + date.format(DATE);
    }

    /**
     * dayOfWeek theo ISO: 1 = Thứ 2 … 7 = Chủ nhật.
     */
    public static String dayName(
            Integer dayOfWeek
    ) {
        if (dayOfWeek == null) {
            return "";
        }

        return switch (dayOfWeek) {
            case 1 ->
                    "Thứ 2";
            case 2 ->
                    "Thứ 3";
            case 3 ->
                    "Thứ 4";
            case 4 ->
                    "Thứ 5";
            case 5 ->
                    "Thứ 6";
            case 6 ->
                    "Thứ 7";
            case 7 ->
                    "Chủ nhật";
            default ->
                    "";
        };
    }

    public static String dayName(
            LocalDate date
    ) {
        return date == null
                ? ""
                : dayName(
                        date.getDayOfWeek()
                                .getValue()
                );
    }

    /**
     * Tên dịch vụ tiếng Việt.
     */
    public static String service(
            ServiceCode code
    ) {
        if (code == null) {
            return "";
        }

        return switch (code) {
            case GYM ->
                    "Gym";
            case BOXING ->
                    "Boxing";
            case PICKLEBALL ->
                    "Pickleball";
        };
    }

    public static String service(
            String code
    ) {
        if (code == null) {
            return "";
        }

        try {
            return service(
                    ServiceCode.valueOf(code)
            );
        } catch (IllegalArgumentException exception) {
            return code;
        }
    }

    /**
     * Map lý do check-in bị từ chối sang tiếng Việt.
     * <p>Có đủ 5 reason (code thật có cả {@code DUPLICATE_CHECKIN}).
     */
    public static String rejectReason(
            String reason
    ) {
        if (reason == null
                || reason.isBlank()) {
            return "Không rõ lý do";
        }

        return switch (reason) {
            case "NO_ACTIVE_MEMBERSHIP" ->
                    "Chưa có gói tập đang hiệu lực";

            case "SERVICE_NOT_INCLUDED" ->
                    "Dịch vụ không có trong gói tập";

            case "MEMBERSHIP_BRANCH_MISMATCH" ->
                    "Sai chi nhánh của gói tập";

            case "MEMBERSHIP_INVALID" ->
                    "Gói chưa tới hạn hoặc đã kết thúc";

            case "DUPLICATE_CHECKIN" ->
                    "Đã check-in dịch vụ này rồi";

            default ->
                    reason;
        };
    }

    /**
     * Cắt danh sách dài, thêm "và còn N mục nữa".
     */
    public static String limit(
            String text,
            int max
    ) {
        if (text == null) {
            return "";
        }

        String[] lines =
                text.split("\n");

        if (lines.length <= max) {
            return text;
        }

        return String.join(
                "\n",
                java.util.Arrays.copyOf(
                        lines,
                        max
                )
        ) + "\n… và còn "
                + (lines.length - max)
                + " mục nữa";
    }

}