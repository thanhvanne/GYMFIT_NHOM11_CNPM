package com.gymfit.chat.nlg;

import com.gymfit.branch.ServiceCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FmtTest {

    private static final ZoneId VN =
            ZoneId.of("Asia/Ho_Chi_Minh");

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "0,          0đ",
            "1000,       1.000đ",
            "1500000,    1.500.000đ",
            "2000000,    2.000.000đ",
            "600000,     600.000đ",
            "100000,     100.000đ",
            "1234567,    1.234.567đ",
            "999,        999đ",
            "12345,      12.345đ"
    })
    @DisplayName("Tiền có dấu chấm phân tách nghìn")
    void money(
            long input,
            String expected
    ) {
        assertEquals(
                expected,
                Fmt.money(
                        BigDecimal.valueOf(input)
                )
        );
    }

    @Test
    @DisplayName("Tiền làm tròn và null-safe")
    void moneyEdge() {

        assertEquals(
                "1.001đ",
                Fmt.money(
                        BigDecimal.valueOf(
                                1000.6
                        )
                )
        );

        assertEquals(
                "0đ",
                Fmt.money(
                        (BigDecimal) null
                )
        );
    }

    @Test
    @DisplayName("Ngày kèm thứ trong tuần")
    void date() {

        assertEquals(
                "04/10/2026 (Chủ nhật)",
                Fmt.date(
                        LocalDate.of(
                                2026,
                                10,
                                4
                        )
                )
        );

        assertEquals(
                "05/10/2026 (Thứ 2)",
                Fmt.date(
                        LocalDate.of(
                                2026,
                                10,
                                5
                        )
                )
        );

        assertEquals(
                "03/10/2026 (Thứ 7)",
                Fmt.date(
                        LocalDate.of(
                                2026,
                                10,
                                3
                        )
                )
        );

        assertEquals(
                "",
                Fmt.date(
                        (LocalDate) null
                )
        );
    }

    @Test
    @DisplayName("2026-10-04T12:00:00Z → 19:00 giờ Việt Nam")
    void timeInVietnam() {

        Instant instant =
                Instant.parse(
                        "2026-10-04T12:00:00Z"
                );

        assertEquals(
                "19:00",
                Fmt.time(
                        instant,
                        VN
                )
        );
    }

    @Test
    @DisplayName("Giờ theo múi giờ chi nhánh (UTC+7)")
    void timeInBranchZone() {

        Instant instant =
                Instant.parse(
                        "2026-10-04T12:00:00Z"
                );

        assertEquals(
                "19:00",
                Fmt.time(
                        instant,
                        ZoneId.of("Asia/Ho_Chi_Minh")
                )
        );

        assertEquals(
                "12:00",
                Fmt.time(
                        instant,
                        ZoneId.of("UTC")
                ),
                "Múi giờ khác phải cho kết quả khác"
        );
    }

    @Test
    @DisplayName("Ngày + giờ kết hợp")
    void dateTime() {

        assertEquals(
                "19:00 04/10/2026",
                Fmt.dateTime(
                        LocalDate.of(
                                2026,
                                10,
                                4
                        ),
                        LocalTime.of(
                                19,
                                0
                        )
                )
        );
    }

    @Test
    @DisplayName("Tên thứ trong tuần 1..7")
    void dayName() {

        assertEquals(
                "Thứ 2",
                Fmt.dayName(1)
        );

        assertEquals(
                "Thứ 7",
                Fmt.dayName(6)
        );

        assertEquals(
                "Chủ nhật",
                Fmt.dayName(7)
        );

        assertEquals(
                "",
                Fmt.dayName(
                        (Integer) null
                )
        );
    }

    @Test
    @DisplayName("Tên dịch vụ")
    void service() {

        assertEquals(
                "Gym",
                Fmt.service(ServiceCode.GYM)
        );

        assertEquals(
                "Boxing",
                Fmt.service(ServiceCode.BOXING)
        );

        assertEquals(
                "Pickleball",
                Fmt.service(ServiceCode.PICKLEBALL)
        );

        assertEquals(
                "Gym",
                Fmt.service("GYM"),
                "Nhận cả dạng String"
        );

        assertEquals(
                "XYZ",
                Fmt.service("XYZ"),
                "Mã lạ thì trả nguyên"
        );
    }

    @Test
    @DisplayName("Map đủ 5 lý do check-in bị từ chối sang tiếng Việt")
    void rejectReason() {

        assertTrue(
                Fmt.rejectReason(
                        "NO_ACTIVE_MEMBERSHIP"
                ).contains("gói tập")
        );

        assertTrue(
                Fmt.rejectReason(
                        "SERVICE_NOT_INCLUDED"
                ).contains("Dịch vụ")
        );

        assertTrue(
                Fmt.rejectReason(
                        "MEMBERSHIP_BRANCH_MISMATCH"
                ).contains("chi nhánh")
        );

        assertTrue(
                Fmt.rejectReason(
                        "MEMBERSHIP_INVALID"
                ).contains("hạn")
        );

        assertTrue(
                Fmt.rejectReason(
                        "DUPLICATE_CHECKIN"
                ).contains("rồi"),
                "DUPLICATE_CHECKIN phải có tiếng Việt"
        );

        assertEquals(
                "Không rõ lý do",
                Fmt.rejectReason(
                        (String) null
                )
        );
    }

    @Test
    @DisplayName("limit() cắt danh sách và báo số mục còn lại")
    void limit() {

        String text =
                "a\nb\nc\nd\ne";

        String result =
                Fmt.limit(
                        text,
                        3
                );

        assertTrue(
                result.startsWith(
                        "a\nb\nc"
                )
        );

        assertTrue(
                result.contains(
                        "còn 2 mục nữa"
                )
        );

        assertEquals(
                text,
                Fmt.limit(
                        text,
                        10
                )
        );
    }

}