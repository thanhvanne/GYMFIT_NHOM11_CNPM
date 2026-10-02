package com.gymfit.chat.nlu;

import com.gymfit.chat.nlu.entity.EntityType;
import com.gymfit.chat.nlu.entity.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse ngày / giờ / khoảng ngày / tiền / thời hạn / mã lịch từ chuỗi {@code plain}
 * (đã bỏ dấu, chữ thường).
 *
 * <p>Quy tắc quan trọng:
 * <ul>
 *     <li>{@code today} luôn được truyền vào — không gọi {@code LocalDate.now()} bên trong.</li>
 *     <li>"hôm nay"/"hôm qua" vừa là DATE vừa là RANGE → điền cả hai.</li>
 *     <li>Thứ trong tuần = lần xuất hiện <b>kéo dài</b> sau hôm nay.</li>
 *     <li>Buổi "chiều/tối/đêm" + giờ ≤ 11 → +12; không có buổi mà giờ ≤ 5 → +12
 *         (phòng tập không mở 1h–5h sáng).</li>
 * </ul>
 */
@Component
@Slf4j
public class DateTimeParser {

    public static final int MAX_SPAN_OVERLAP_CHECK = 0;

    // ------------------------------------------------------------------
    // Mẫu khoảng báo cáo (ưu tiên cao nhất)
    // ------------------------------------------------------------------

    private static final Pattern FROM_TO = Pattern.compile(
            "\\btu\\s+(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?"
                    + "\\s+den\\s+(?:(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?"
                    + "|(hom\\s*nay|ngay\\s*mai))\\b"
    );

    private static final Pattern DAYS_AGO = Pattern.compile(
            "\\b(\\d{1,3})\\s*ngay\\s*qua\\b"
    );

    private static final Pattern THIS_WEEK = Pattern.compile(
            "\\btuan\\s*(nay|truoc)\\b"
    );

    private static final Pattern THIS_MONTH = Pattern.compile(
            "\\bthang\\s*(nay|truoc)\\b"
    );

    private static final Pattern YESTERDAY = Pattern.compile(
            "\\bhom\\s*qua\\b"
    );

    private static final Pattern TODAY = Pattern.compile(
            "\\bhom\\s*nay\\b"
    );

    // ------------------------------------------------------------------
    // Mẫu ngày đơn
    // ------------------------------------------------------------------

    private static final Pattern TOMORROW = Pattern.compile(
            "\\bngay\\s*(mai|kia|mot)\\b|\\bmai\\b"
    );

    private static final Pattern WEEKDAY = Pattern.compile(
            "\\bthu\\s*([2-7])\\b"
    );

    private static final Pattern SUNDAY = Pattern.compile(
            "\\b(chu\\s*nhat|cn)\\b"
    );

    private static final Pattern DAY_NEXT_WEEK = Pattern.compile(
            "\\b(tuan\\s*sau|tuan\\s*toi)\\b"
    );

    private static final Pattern DAY_WITH_PREFIX = Pattern.compile(
            "\\b(vao|ngay)\\s*(\\d{1,2})"
                    + "(?:/(\\d{1,2}))?(?:/(\\d{4}))?\\b"
    );

    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?(?!\\d)"
    );

    private static final Pattern DASH_DATE = Pattern.compile(
            "(?<!\\d)(\\d{1,2})-(\\d{1,2})(?:/(\\d{4}))?(?!\\d)"
    );

    private static final Pattern JUST_NOW = Pattern.compile(
            "\\bnay\\b"
    );

    // ------------------------------------------------------------------
    // Mẫu giờ
    // ------------------------------------------------------------------

    private static final Pattern TIME_COLON = Pattern.compile(
            "(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)"
    );

    private static final Pattern TIME_HOUR = Pattern.compile(
            "(?<![\\d:])(\\d{1,2})\\s*(?:h|gio)(?:\\s*(\\d{2}))?(?!\\d)"
    );

    /** Buổi trong ngày, đứng sau giờ (hoặc đứng riêng). */
    private static final Pattern SESSION = Pattern.compile(
            "\\b(sang|trua|chieu|toi|dem|nua\\s*dem)\\b"
    );

    private static final Pattern HALF_PAST = Pattern.compile(
            "\\bruoi\\b"
    );

    private static final Pattern WEEKDAY_SHORT = Pattern.compile(
            "\\b(cn)\\b"
    );

    // ------------------------------------------------------------------
    // Mẫu tiền & thời hạn & mã
    // ------------------------------------------------------------------

    private static final Pattern MONEY = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:[.,]\\d{3})+|\\d+)"
                    + "\\s*(trieu|tr|nghin|ng|k|dong|vn[dđ])\\b"
    );

    /** "1.5 triệu", "1,5tr" — phần thập phân chỉ 1 chữ số. */
    private static final Pattern MONEY_DECIMAL = Pattern.compile(
            "(?<!\\d)(\\d+)[.,](\\d)\\s*(trieu|tr|nghin|ng|k)\\b"
    );

    /** "1 triệu 5" (rút gọn) = 1.500.000 */
    private static final Pattern MONEY_SHORTHAND = Pattern.compile(
            "(?<!\\d)(\\d+)\\s*(trieu|tr)\\s+(\\d)\\b"
    );

    private static final Pattern MONEY_PLAIN_GROUPED = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:\\.\\d{3}){1,2})(?!\\d)"
    );

    private static final Pattern MONEY_SIX_DIGITS = Pattern.compile(
            "(?<!\\d)(\\d{6})(?!\\d)"
    );

    private static final Pattern DURATION = Pattern.compile(
            "\\b(\\d{1,2})\\s*(thang|nam|tuan)\\b"
    );

    private static final Pattern CODE = Pattern.compile(
            "\\b(BOOK|ORD|PLAN|MEM|PROD|BILL|PAY)_[0-9A-F]{16}\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final BigDecimal THOUSAND =
            new BigDecimal("1000");

    private static final BigDecimal MILLION =
            new BigDecimal("1000000");

    /**
     * Kết quả parse. Giá trị null nghĩa là không nhận diện được.
     */
    public record Result(
            List<Span> spans,
            LocalDate date,
            LocalTime time,
            LocalDate rangeFrom,
            LocalDate rangeTo,
            BigDecimal money,
            Integer durationDays,
            String bookingCode
    ) {

        static Result empty() {
            return new Result(
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    

    public Result parse(
            String plain,
            LocalDate today
    ) {
        if (plain == null
                || plain.isBlank()
                || today == null) {
            return Result.empty();
        }

        Collector collector =
                new Collector(
                        plain,
                        today
                );

        // Thứ tự = độ ưu tiên: cụm dài/đặc biệt trước, cụm chung sau.
        parseRange(collector);
        parseSingleDate(collector);
        parseTime(collector);
        parseMoney(collector);
        parseDuration(collector);
        parseCode(collector);

        return new Result(
                collector.spans(),
                collector.date(),
                collector.time(),
                collector.rangeFrom(),
                collector.rangeTo(),
                collector.money(),
                collector.durationDays(),
                collector.bookingCode()
        );
    }

    // ------------------------------------------------------------------
    // Khoảng ngày
    // ------------------------------------------------------------------

    private void parseRange(
            Collector collector
    ) {
        Matcher matcher =
                FROM_TO.matcher(collector.plain());

        while (matcher.find()) {

            LocalDate from =
                    toDate(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            collector.today(),
                            false
                    );

            LocalDate to;

            if (matcher.group(4) != null) {

                to =
                        toDate(
                                matcher.group(4),
                                matcher.group(5),
                                matcher.group(6),
                                collector.today(),
                                false
                        );

            } else {

                // "đến hôm nay" / "đến ngày mai"
                String relative =
                        matcher.group(7);

                to =
                        relative.startsWith(
                                "hom"
                        )
                                ? collector.today()
                                : collector.today()
                                .plusDays(1);
            }

            if (from == null || to == null || to.isBefore(from)) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.rangeFrom(from);
                collector.rangeTo(to);
            }
        }

        matcher =
                DAYS_AGO.matcher(collector.plain());

        while (matcher.find()) {

            int days =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            if (days <= 0) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.rangeFrom(
                        collector.today()
                                .minus(days, ChronoUnit.DAYS)
                );

                collector.rangeTo(
                        collector.today()
                                .minusDays(1)
                );
            }
        }

        matcher =
                THIS_WEEK.matcher(collector.plain());

        while (matcher.find()) {

            // DayOfWeek.with(MONDAY) luôn lùi về đầu tuần ISO đang chứa today
            // (thứ 7 → thứ 2 của tuần đó, tức 2026-09-28).
            LocalDate monday =
                    collector.today()
                            .with(
                                    DayOfWeek.of(
                                            1
                                    )
                            );

            boolean current =
                    "nay".equals(
                            matcher.group(1)
                    );

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.rangeFrom(
                        current
                                ? monday
                                : monday.minusWeeks(1)
                );

                collector.rangeTo(
                        current
                                ? collector.today()
                                : monday.minusDays(1)
                );
            }
        }

        matcher =
                THIS_MONTH.matcher(collector.plain());

        while (matcher.find()) {

            LocalDate first =
                    collector.today()
                            .withDayOfMonth(1);

            boolean current =
                    "nay".equals(
                            matcher.group(1)
                    );

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {

                if (current) {

                    collector.rangeFrom(first);
                    collector.rangeTo(
                            collector.today()
                    );

                } else {

                    LocalDate previous =
                            first.minusMonths(1);

                    collector.rangeFrom(previous);
                    collector.rangeTo(
                            first.minusDays(1)
                    );
                }
            }
        }

        matcher =
                YESTERDAY.matcher(collector.plain());

        while (matcher.find()) {

            if (!collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                continue;
            }

            LocalDate yesterday =
                    collector.today()
                            .minusDays(1);

            collector.date(yesterday);
            collector.rangeFrom(yesterday);
            collector.rangeTo(yesterday);
        }
    }

    // ------------------------------------------------------------------
    // Ngày đơn
    // ------------------------------------------------------------------

    private void parseSingleDate(
            Collector collector
    ) {

        // "hôm nay" vừa DATE vừa RANGE
        Matcher matcher =
                TODAY.matcher(collector.plain());

        while (matcher.find()) {

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.date(
                        collector.today()
                );

                collector.rangeFrom(
                        collector.today()
                );

                collector.rangeTo(
                        collector.today()
                );
            }
        }

        matcher =
                TOMORROW.matcher(collector.plain());

        while (matcher.find()) {

            boolean second =
                    matcher.group(1) != null
                            && ("kia".equals(
                            matcher.group(1)
                    ) || "mot".equals(
                            matcher.group(1)
                    ));

            boolean hasPrefix =
                    matcher.group(1) != null;

            if (!collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                continue;
            }

            collector.date(
                    collector.today()
                            .plusDays(
                                    second
                                            ? 2
                                            : 1
                            )
            );

            if (hasPrefix) {
                continue;
            }
        }

        matcher =
                DAY_NEXT_WEEK.matcher(collector.plain());

        boolean nextWeek =
                matcher.find();

        // matcher.end() chỉ hợp lệ sau khi find() trả true.
        int nextWeekEnd =
                nextWeek
                        ? matcher.end()
                        : 0;

        matcher =
                WEEKDAY.matcher(collector.plain());

        while (matcher.find()) {

            int vietnamese =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            // "thứ 2" = Thứ Hai = ISO 1 (không phải ISO 2).
            DayOfWeek target =
                    DayOfWeek.of(vietnamese - 1);

            if (!collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                continue;
            }

            LocalDate result =
                    nextWeekday(
                            collector.today(),
                            target
                    );

            if (nextWeek
                    && nextWeekEnd > 0) {
                result =
                        result.plusWeeks(1);
            }

            collector.date(result);
        }

        matcher =
                SUNDAY.matcher(collector.plain());

        while (matcher.find()) {

            String text =
                    matcher.group(1);

            // "chu nhat" (có khoảng trắng) luôn hiểu là Chủ nhật;
            // "cn" trần thì phải có ngữ cảnh "thu/vao/ngay" phía trước.
            boolean explicit =
                    text.contains(" ");

            if (!explicit
                    && !hasWeekdayContext(
                    collector.plain(),
                    matcher.start()
            )) {
                continue;
            }

            if (!collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                continue;
            }

            LocalDate result =
                    nextWeekday(
                            collector.today(),
                            DayOfWeek.SUNDAY
                    );

            if (nextWeek
                    && nextWeekEnd > 0) {
                result =
                        result.plusWeeks(1);
            }

            collector.date(result);
        }

        matcher =
                DAY_WITH_PREFIX.matcher(collector.plain());

        while (matcher.find()) {

            LocalDate value =
                    toDate(
                            matcher.group(2),
                            matcher.group(3),
                            matcher.group(4),
                            collector.today(),
                            true
                    );

            if (value == null) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.date(value);
            }
        }

        matcher =
                NUMERIC_DATE.matcher(collector.plain());

        while (matcher.find()) {

            LocalDate value =
                    toDate(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            collector.today(),
                            true
                    );

            if (value == null) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.date(value);
            }
        }

        matcher =
                DASH_DATE.matcher(collector.plain());

        while (matcher.find()) {

            LocalDate value =
                    toDate(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            collector.today(),
                            true
                    );

            if (value == null) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.date(value);
            }
        }

        matcher =
                JUST_NOW.matcher(collector.plain());

        while (matcher.find()) {

            if (collector.date() != null) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DATE
            )) {
                collector.date(
                        collector.today()
                );
            }
        }
    }

    /**
     * "cn" chỉ được hiểu là Chủ nhật khi đứng sau "thu"/"vao"/"ngay".
     */
    private static boolean hasWeekdayContext(
            String plain,
            int start
    ) {
        String prefix =
                plain.substring(
                                0,
                                Math.max(
                                        0,
                                        start - 12
                                )
                        )
                        .trim();

        return prefix.endsWith("thu")
                || prefix.endsWith("vao")
                || prefix.endsWith("ngay");
    }

    private static LocalDate nextWeekday(
            LocalDate from,
            DayOfWeek target
    ) {
        int delta =
                target.getValue()
                        - from.getDayOfWeek()
                                .getValue();

        if (delta <= 0) {
            delta += 7;
        }

        return from.plusDays(delta);
    }

    /**
     * dd/MM[/yyyy].
     *
     * <p>{@code month == null} -> chi co ngay ("ngay 5"): dung thang/nam cua
     * {@code today}; neu ngay do da qua thi lui sang thang ke tiep.
     *
     * <p>{@code rollToNextYear}: dd/MM da qua trong nam hien tai -> nam sau
     * ("dat lich ngay 5/1"). Khoang bao cao thi KHONG roll
     * ("tu 1/9 den 30/9" phai la nam nay).
     */
    private static LocalDate toDate(
            String day,
            String month,
            String year,
            LocalDate today,
            boolean rollToNextYear
    ) {
        if (day == null) {
            return null;
        }

        int dayValue;

        try {
            dayValue =
                    Integer.parseInt(day);
        } catch (NumberFormatException exception) {
            return null;
        }

        if (dayValue < 1
                || dayValue > 31) {
            return null;
        }

        // Chi co ngay: "ngay 5"
        if (month == null) {
            return withDay(
                    today,
                    today.getYear(),
                    today.getMonthValue(),
                    dayValue,
                    true
            );
        }

        int monthValue;

        try {
            monthValue =
                    Integer.parseInt(month);
        } catch (NumberFormatException exception) {
            return null;
        }

        if (monthValue < 1
                || monthValue > 12) {
            return null;
        }

        if (year != null
                && !year.isBlank()) {

            try {

                return LocalDate.of(
                        Integer.parseInt(year),
                        monthValue,
                        dayValue
                );

            } catch (Exception exception) {
                return null;
            }
        }

        LocalDate candidate =
                withDay(
                        today,
                        today.getYear(),
                        monthValue,
                        dayValue,
                        false
                );

        if (candidate == null) {
            return null;
        }

        if (!candidate.isBefore(today)
                || !rollToNextYear) {
            return candidate;
        }

        return withDay(
                today,
                today.getYear() + 1,
                monthValue,
                dayValue,
                false
        );
    }

    private static LocalDate withDay(
            LocalDate today,
            int year,
            int month,
            int day,
            boolean rollDayForward
    ) {
        try {

            LocalDate candidate =
                    LocalDate.of(
                            year,
                            month,
                            day
                    );

            // Chi "ngay 5" (thieu thang) moi lui sang thang ke tiep khi da qua.
            // dd/mm tuoi minh thi giu nguyen: "tu 1/10 den hom nay" =
            // 01/10 -> 03/10.
            if (rollDayForward
                    && month == today.getMonthValue()
                    && year == today.getYear()
                    && candidate.isBefore(today)) {
                return candidate.plusMonths(1);
            }

            return candidate;

        } catch (Exception exception) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Giờ
    // ------------------------------------------------------------------

    private void parseTime(
            Collector collector
    ) {

        Matcher matcher =
                TIME_COLON.matcher(collector.plain());

        while (matcher.find()) {

            LocalTime value =
                    toTime(
                            Integer.parseInt(
                                    matcher.group(1)
                            ),
                            Integer.parseInt(
                                    matcher.group(2)
                            )
                    );

            if (value == null) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.TIME
            )) {
                collector.time(value);
            }
        }

        matcher =
                TIME_HOUR.matcher(collector.plain());

        while (matcher.find()) {

            int hour =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            int minute =
                    0;

            if (matcher.group(2) != null) {

                try {
                    minute =
                            Integer.parseInt(
                                    matcher.group(2)
                            );
                } catch (NumberFormatException exception) {
                    minute = 0;
                }
            }

            boolean halfPast =
                    containsHalfPast(
                            collector.plain(),
                            matcher.end()
                    );

            if (halfPast) {
                minute = 30;
            }

            Session session =
                    sessionAfter(
                            collector.plain(),
                            matcher.end()
                    );

            LocalTime value =
                    toTime(
                            applySession(
                                    hour,
                                    session
                            ),
                            minute
                    );

            if (value == null) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.TIME
            )) {
                collector.time(value);
            }
        }
    }

    /**
     * "ruoi" chỉ được tính nếu nằm ngay sau khớp giờ (tối đa 6 ký tự).
     */
    private static boolean containsHalfPast(
            String plain,
            int from
    ) {
        if (from >= plain.length()) {
            return false;
        }

        Matcher matcher =
                HALF_PAST.matcher(plain);

        return matcher.find(from)
                && matcher.start() - from <= 6;
    }

    private enum Session {

        NONE,
        MORNING,
        NOON,
        AFTERNOON,
        EVENING,
        NIGHT,
        MIDNIGHT

    }

    private static Session sessionAfter(
            String plain,
            int from
    ) {
        Matcher matcher =
                SESSION.matcher(plain);

        if (!matcher.find(from)) {
            return Session.NONE;
        }

        // Buổi phải nằm ngay sau giờ, cách không quá 3 từ.
        String between =
                plain.substring(
                        from,
                        Math.min(
                                plain.length(),
                                matcher.start()
                        )
                ).trim();

        if (between.split(" ")
                .length > 3) {
            return Session.NONE;
        }

        String text =
                matcher.group(1);

        return switch (text) {
            case "sang" ->
                    Session.MORNING;

            case "trua" ->
                    Session.NOON;

            case "chieu" ->
                    Session.AFTERNOON;

            case "toi" ->
                    Session.EVENING;

            case "nua dem" ->
                    Session.MIDNIGHT;

            default ->
                    Session.NIGHT;
        };
    }

    /**
     * Buổi chiều/tối/đêm với giờ ≤ 11 → +12 (7h tối = 19:00).
     * Không có buổi mà giờ ≤ 5 → +12 (phòng tập không mở 1h–5h sáng).
     */
    private static int applySession(
            int hour,
            Session session
    ) {
        Session actual =
                session == null
                        ? Session.NONE
                        : session;

        return switch (actual) {
            case AFTERNOON, EVENING, NIGHT ->
                    hour <= 11
                            ? hour + 12
                            : hour;

            case MIDNIGHT ->
                    0;

            case NONE ->
                    // 3h, 2 giờ, 1h…5h mà không nói buổi → hiểu là buổi chiều.
                    hour >= 1 && hour <= 5
                            ? hour + 12
                            : hour;

            default ->
                    hour;
        };
    }

    private static LocalTime toTime(
            int hour,
            int minute
    ) {
        if (hour < 0
                || hour > 23
                || minute < 0
                || minute > 59) {
            return null;
        }

        return LocalTime.of(
                hour,
                minute
        );
    }

    // ------------------------------------------------------------------
    // Tiền
    // ------------------------------------------------------------------

    private void parseMoney(
            Collector collector
    ) {

        // "1 triệu 5" phải được ưu tiên trước các mẫu khác.
        Matcher shorthand =
                MONEY_SHORTHAND.matcher(
                        collector.plain()
                );

        while (shorthand.find()) {

            BigDecimal value =
                    new BigDecimal(
                            shorthand.group(1)
                    ).multiply(MILLION)
                            .add(
                                    new BigDecimal(
                                            shorthand.group(3)
                                    ).multiply(
                                            new BigDecimal("100000")
                                    )
                            );

            if (collector.claim(
                    shorthand.start(),
                    shorthand.end(),
                    EntityType.MONEY
            )) {
                collector.money(value);
            }
        }

        Matcher decimal =
                MONEY_DECIMAL.matcher(
                        collector.plain()
                );

        while (decimal.find()) {

            BigDecimal value =
                    new BigDecimal(
                            decimal.group(1)
                                    + "."
                                    + decimal.group(2)
                    );

            value =
                    value.multiply(
                            multiplier(
                                    decimal.group(3)
                            )
                    );

            if (value.signum() <= 0) {
                continue;
            }

            if (collector.claim(
                    decimal.start(),
                    decimal.end(),
                    EntityType.MONEY
            )) {
                collector.money(value);
            }
        }

        Matcher matcher =
                MONEY.matcher(collector.plain());

        while (matcher.find()) {

            BigDecimal value =
                    toMoney(
                            matcher.group(1),
                            matcher.group(2)
                    );

            if (value == null
                    || value.signum() <= 0) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.MONEY
            )) {
                collector.money(value);
            }
        }

        matcher =
                MONEY_PLAIN_GROUPED.matcher(
                        collector.plain()
                );

        while (matcher.find()) {

            BigDecimal value =
                    new BigDecimal(
                            matcher.group(1)
                                    .replace(".", "")
                    );

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.MONEY
            )) {
                collector.money(value);
            }
        }

        matcher =
                MONEY_SIX_DIGITS.matcher(
                        collector.plain()
                );

        while (matcher.find()) {

            BigDecimal value =
                    new BigDecimal(
                            matcher.group(1)
                    );

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.MONEY
            )) {
                collector.money(value);
            }
        }
    }

    private static BigDecimal toMoney(
            String number,
            String unit
    ) {
        BigDecimal value;

        try {
            value =
                    new BigDecimal(
                            number.replace(
                                    ",",
                                    ""
                            ).replace(
                                    ".",
                                    ""
                            )
                    );
        } catch (NumberFormatException exception) {
            return null;
        }

        return value.multiply(
                multiplier(unit)
        );
    }

    private static BigDecimal multiplier(
            String unit
    ) {
        return switch (unit) {
            case "trieu", "tr" ->
                    MILLION;

            case "nghin", "ng", "k" ->
                    THOUSAND;

            default ->
                    BigDecimal.ONE;
        };
    }

    // ------------------------------------------------------------------
    // Thời hạn & mã
    // ------------------------------------------------------------------

    private void parseDuration(
            Collector collector
    ) {
        Matcher matcher =
                DURATION.matcher(collector.plain());

        while (matcher.find()) {

            int amount =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            int days =
                    switch (matcher.group(2)) {
                        case "thang" ->
                                amount * 30;

                        case "nam" ->
                                amount * 365;

                        default ->
                                amount * 7;
                    };

            if (amount <= 0) {
                continue;
            }

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.DURATION
            )) {
                collector.durationDays(days);
            }
        }
    }

    private void parseCode(
            Collector collector
    ) {
        Matcher matcher =
                CODE.matcher(collector.plain());

        while (matcher.find()) {

            if (collector.claim(
                    matcher.start(),
                    matcher.end(),
                    EntityType.BOOKING_CODE
            )) {
                collector.bookingCode(
                        matcher.group()
                                .toUpperCase()
                );
            }
        }
    }

    // ------------------------------------------------------------------
    // Bộ gom kết quả + chống trùng lặp span
    // ------------------------------------------------------------------

    /**
     * Gom span đã chiếm, loại trùng lặp theo khoảng [start, end).
     */
    private static final class Collector {

        private final String plain;
        private final LocalDate today;
        private final List<Span> spans =
                new ArrayList<>();

        private LocalDate date;
        private LocalTime time;
        private LocalDate rangeFrom;
        private LocalDate rangeTo;
        private BigDecimal money;
        private Integer durationDays;
        private String bookingCode;

        private Collector(
                String plain,
                LocalDate today
        ) {
            this.plain =
                    plain;

            this.today =
                    today;
        }

        String plain() {
            return plain;
        }

        LocalDate today() {
            return today;
        }

        List<Span> spans() {
            return List.copyOf(spans);
        }

        LocalDate date() {
            return date;
        }

        LocalTime time() {
            return time;
        }

        LocalDate rangeFrom() {
            return rangeFrom;
        }

        LocalDate rangeTo() {
            return rangeTo;
        }

        BigDecimal money() {
            return money;
        }

        Integer durationDays() {
            return durationDays;
        }

        String bookingCode() {
            return bookingCode;
        }

        void date(
                LocalDate value
        ) {
            if (date == null) {
                date = value;
            }
        }

        void time(
                LocalTime value
        ) {
            if (time == null) {
                time = value;
            }
        }

        void rangeFrom(
                LocalDate value
        ) {
            if (rangeFrom == null) {
                rangeFrom = value;
            }
        }

        void rangeTo(
                LocalDate value
        ) {
            if (rangeTo == null) {
                rangeTo = value;
            }
        }

        void money(
                BigDecimal value
        ) {
            if (money == null) {

                // Luôn trả về scale 0 (1500000.0 → 1500000) để so sánh ổn định.
                money =
                        value.stripTrailingZeros();

                if (money.scale() < 0) {
                    money =
                            money.setScale(0);
                }
            }
        }

        void durationDays(
                Integer value
        ) {
            if (durationDays == null) {
                durationDays = value;
            }
        }

        void bookingCode(
                String value
        ) {
            if (bookingCode == null) {
                bookingCode = value;
            }
        }

        /**
         * @return true nếu vùng [start, end) chưa bị chiếm → được thêm span
         */
        boolean claim(
                int start,
                int end,
                EntityType type
        ) {

            for (Span occupied : spans) {

                if (start < occupied.end()
                        && occupied.start() < end) {
                    return false;
                }
            }

            spans.add(
                    new Span(
                            type,
                            start,
                            end,
                            plain.substring(
                                    start,
                                    Math.min(
                                            end,
                                            plain.length()
                                    )
                            )
                    )
            );

            return true;
        }

    }

}
