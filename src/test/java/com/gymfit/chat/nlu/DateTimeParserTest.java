package com.gymfit.chat.nlu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bảng đặc tả của plan T2.
 * <p><b>today = 2026-10-03 (Thứ 7, ISO 6)</b>
 */
class DateTimeParserTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 3);

    private DateTimeParser parser;
    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        parser =
                new DateTimeParser();

        normalizer =
                new TextNormalizer();

        normalizer.load();
    }

    private DateTimeParser.Result parse(
            String raw
    ) {
        return parser.parse(
                normalizer.normalize(raw)
                        .plain(),
                TODAY
        );
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Ngày đơn")
    class SingleDate {

        @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
        @MethodSource(
                "com.gymfit.chat.nlu.DateTimeParserTest#dates"
        )
        @DisplayName("Ngày tương đối & tuyệt đối")
        void date(
                String raw,
                String expected
        ) {
            assertEquals(
                    LocalDate.parse(expected),
                    parse(raw).date(),
                    raw
            );
        }

        @Test
        @DisplayName("'hôm nay' điền cả DATE lẫn RANGE")
        void todayFillsBoth() {
            DateTimeParser.Result result =
                    parse("hôm nay");

            assertEquals(
                    TODAY,
                    result.date()
            );

            assertEquals(
                    TODAY,
                    result.rangeFrom()
            );

            assertEquals(
                    TODAY,
                    result.rangeTo()
            );
        }

        @Test
        @DisplayName("'nay' trần → hôm nay")
        void bareNow() {
            assertEquals(
                    TODAY,
                    parse("hôm nay vậy nay")
                            .date()
            );
        }

        @Test
        @DisplayName("'ngày một' = ngày kia")
        void ngayMot() {
            assertEquals(
                    LocalDate.of(2026, 10, 5),
                    parse("ngày một")
                            .date()
            );
        }

        @Test
        @DisplayName("'tuan sau thu 2' = thứ 2 tuần kế tiếp")
        void nextWeekMonday() {
            assertEquals(
                    LocalDate.of(2026, 10, 12),
                    parse("thứ 2 tuần sau")
                            .date()
            );
        }

        @Test
        @DisplayName("'chu nhat' = Chủ nhật kế tiếp")
        void sunday() {
            assertEquals(
                    LocalDate.of(2026, 10, 4),
                    parse("chủ nhật")
                            .date()
            );

            assertEquals(
                    LocalDate.of(2026, 10, 4),
                    parse("ngày chủ nhật")
                            .date()
            );
        }

        @Test
        @DisplayName("'cn' trần không được hiểu là Chủ nhật")
        void cnNeedsContext() {
            assertNull(
                    parse("số cn của tôi")
                            .date()
            );
        }

        @Test
        @DisplayName("dd/mm đã qua trong năm → năm sau")
        void dateRollsToNextYear() {
            assertEquals(
                    LocalDate.of(2027, 1, 5),
                    parse("5/1")
                            .date()
            );
        }

        @Test
        @DisplayName("dd/mm/yyyy giữ nguyên năm")
        void explicitYear() {
            assertEquals(
                    LocalDate.of(2027, 10, 5),
                    parse("5/10/2027")
                            .date()
            );
        }

        @Test
        @DisplayName("Ngày không hợp lệ bị bỏ qua")
        void invalidDate() {
            assertNull(
                    parse("32/13")
                            .date()
            );
        }

    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Giờ")
    class Time {

        @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
        @MethodSource(
                "com.gymfit.chat.nlu.DateTimeParserTest#times"
        )
        @DisplayName("Parse giờ theo buổi")
        void time(
                String raw,
                String expected
        ) {
            assertEquals(
                    LocalTime.parse(expected),
                    parse(raw).time(),
                    raw
            );
        }

        @Test
        @DisplayName("'nửa đêm' → 00:00")
        void midnight() {
            assertEquals(
                    LocalTime.of(0, 0),
                    parse("12 giờ nửa đêm")
                            .time()
            );
        }

        @Test
        @DisplayName("Giờ vô lý bị bỏ qua")
        void invalidTime() {
            assertNull(
                    parse("99:99")
                            .time()
            );
        }

    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Khoảng ngày báo cáo")
    class Range {

        @ParameterizedTest(name = "[{index}] \"{0}\" → {1} .. {2}")
        @MethodSource(
                "com.gymfit.chat.nlu.DateTimeParserTest#ranges"
        )
        @DisplayName("Khoảng ngày")
        void range(
                String raw,
                String from,
                String to
        ) {
            DateTimeParser.Result result =
                    parse(raw);

            assertEquals(
                    LocalDate.parse(from),
                    result.rangeFrom(),
                    raw
            );

            assertEquals(
                    LocalDate.parse(to),
                    result.rangeTo(),
                    raw
            );
        }

        @Test
        @DisplayName("'hôm qua' điền cả DATE lẫn RANGE")
        void yesterdayFillsBoth() {
            DateTimeParser.Result result =
                    parse("hôm qua");

            assertEquals(
                    LocalDate.of(2026, 10, 2),
                    result.date()
            );

            assertEquals(
                    LocalDate.of(2026, 10, 2),
                    result.rangeFrom()
            );
        }

    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tiền, thời hạn, mã")
    class Others {

        @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
        @MethodSource(
                "com.gymfit.chat.nlu.DateTimeParserTest#moneys"
        )
        @DisplayName("Tiền VND")
        void money(
                String raw,
                long expected
        ) {
            assertEquals(
                    new BigDecimal(expected),
                    parse(raw).money(),
                    raw
            );
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → {1} ngày")
        @MethodSource(
                "com.gymfit.chat.nlu.DateTimeParserTest#durations"
        )
        @DisplayName("Thời hạn → số ngày")
        void duration(
                String raw,
                int expected
        ) {
            assertEquals(
                    expected,
                    parse(raw).durationDays(),
                    raw
            );
        }

        @Test
        @DisplayName("Mã lịch")
        void bookingCode() {
            assertEquals(
                    "BOOK_0123456789ABCDEF",
                    parse("mã BOOK_0123456789ABCDEF")
                            .bookingCode()
            );
        }

        @Test
        @DisplayName("Câu không có gì → toàn null")
        void nothing() {
            DateTimeParser.Result result =
                    parse("cho tôi hỏi về gói tập");

            assertNull(
                    result.date()
            );

            assertNull(
                    result.time()
            );

            assertNull(
                    result.money()
            );

            assertNull(
                    result.bookingCode()
            );
        }

        @Test
        @DisplayName("null/rỗng không ném lỗi")
        void nullInput() {
            assertNull(
                    parser.parse(
                            null,
                            TODAY
                    )
                            .date()
            );

            assertNull(
                    parser.parse(
                            "  ",
                            TODAY
                    )
                            .date()
            );

            assertNull(
                    parser.parse(
                            "mai",
                            null
                    )
                            .date()
            );
        }

    }

    // ==================================================================
    // Nguồn dữ liệu cho parameterized test
    // ==================================================================

    static Stream<Arguments> dates() {
        return Stream.of(
                Arguments.of("hôm nay", "2026-10-03"),
                Arguments.of("nay", "2026-10-03"),
                Arguments.of("ngày mai", "2026-10-04"),
                Arguments.of("mai", "2026-10-04"),
                Arguments.of("ngày kia", "2026-10-05"),
                Arguments.of("ngày một", "2026-10-05"),
                Arguments.of("thứ 2", "2026-10-05"),
                Arguments.of("thứ 3", "2026-10-06"),
                Arguments.of("thứ 4", "2026-10-07"),
                Arguments.of("thứ 5", "2026-10-08"),
                Arguments.of("thứ 6", "2026-10-09"),
                Arguments.of("thứ 7", "2026-10-10"),
                Arguments.of("chủ nhật", "2026-10-04"),
                Arguments.of("5/10", "2026-10-05"),
                Arguments.of("ngày 5/10", "2026-10-05"),
                Arguments.of("ngày 20/12", "2026-12-20"),
                Arguments.of("ngày 5", "2026-10-05"),
                Arguments.of("5-10", "2026-10-05"),
                Arguments.of("ngày mai 5/10", "2026-10-04")
        );
    }

    static Stream<Arguments> times() {
        return Stream.of(
                Arguments.of("7h", "07:00"),
                Arguments.of("7h30", "07:30"),
                Arguments.of("19h", "19:00"),
                Arguments.of("19:00", "19:00"),
                Arguments.of("7 giờ", "07:00"),
                Arguments.of("7 giờ rưỡi", "07:30"),
                Arguments.of("7 giờ 15", "07:15"),
                Arguments.of("7h sáng", "07:00"),
                Arguments.of("7h sáng mai", "07:00"),
                Arguments.of("10h sáng", "10:00"),
                Arguments.of("12h trưa", "12:00"),
                Arguments.of("6h chiều", "18:00"),
                Arguments.of("7h tối", "19:00"),
                Arguments.of("11h đêm", "23:00"),
                Arguments.of("6h tối", "18:00"),
                Arguments.of("9h", "09:00"),
                Arguments.of("11h", "11:00"),
                // Không có buổi mà giờ <= 5 -> +12 (gym không mở 1h-5h sáng)
                Arguments.of("3h", "15:00"),
                Arguments.of("2 giờ", "14:00"),
                Arguments.of("5h", "17:00"),
                Arguments.of("6h", "06:00")
        );
    }

    static Stream<Arguments> ranges() {
        return Stream.of(
                Arguments.of(
                        "hôm nay",
                        "2026-10-03",
                        "2026-10-03"
                ),
                Arguments.of(
                        "hôm qua",
                        "2026-10-02",
                        "2026-10-02"
                ),
                Arguments.of(
                        "tuần này",
                        "2026-09-28",
                        "2026-10-03"
                ),
                Arguments.of(
                        "tuần trước",
                        "2026-09-21",
                        "2026-09-27"
                ),
                Arguments.of(
                        "tháng này",
                        "2026-10-01",
                        "2026-10-03"
                ),
                Arguments.of(
                        "tháng trước",
                        "2026-09-01",
                        "2026-09-30"
                ),
                Arguments.of(
                        "7 ngày qua",
                        "2026-09-26",
                        "2026-10-02"
                ),
                Arguments.of(
                        "30 ngày qua",
                        "2026-09-03",
                        "2026-10-02"
                ),
                Arguments.of(
                        "từ 1/9 đến 30/9",
                        "2026-09-01",
                        "2026-09-30"
                ),
                Arguments.of(
                        "từ 1/10 đến hôm nay",
                        "2026-10-01",
                        "2026-10-03"
                )
        );
    }

    static Stream<Arguments> moneys() {
        return Stream.of(
                Arguments.of("500k", 500_000L),
                Arguments.of("500.000", 500_000L),
                Arguments.of("1.000.000", 1_000_000L),
                Arguments.of("1tr", 1_000_000L),
                Arguments.of("1,5tr", 1_500_000L),
                Arguments.of("1.5 triệu", 1_500_000L),
                Arguments.of("1 triệu 5", 1_500_000L),
                Arguments.of("500 nghìn", 500_000L),
                Arguments.of("2 triệu", 2_000_000L),
                Arguments.of("600k", 600_000L),
                Arguments.of("600000", 600_000L),
                Arguments.of("300.000đ", 300_000L),
                Arguments.of("5 triệu", 5_000_000L)
        );
    }

    static Stream<Arguments> durations() {
        return Stream.of(
                Arguments.of("gói 1 tháng", 30),
                Arguments.of("gói 3 tháng", 90),
                Arguments.of("gói 6 tháng", 180),
                Arguments.of("gói 12 tháng", 360),
                Arguments.of("gói 1 năm", 365),
                Arguments.of("gói 2 tuần", 14)
        );
    }

}