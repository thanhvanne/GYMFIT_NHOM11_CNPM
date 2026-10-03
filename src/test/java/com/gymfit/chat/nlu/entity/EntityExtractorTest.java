package com.gymfit.chat.nlu.entity;

import com.gymfit.branch.ServiceCode;
import com.gymfit.chat.nlu.DateTimeParser;
import com.gymfit.chat.nlu.NormalizedText;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.plan.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * today = 2026-10-03 (Thứ 7).
 */
class EntityExtractorTest {

    /** 2026-10-03 là Thứ 7. */
    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 3);

    private TextNormalizer normalizer;
    private EntityExtractor extractor;

    @BeforeEach
    void setUp() {
        normalizer =
                new TextNormalizer();

        normalizer.load();

        extractor =
                new EntityExtractor(
                        new FakeGazetteer(),
                        new DateTimeParser()
                );

        extractor.load();
    }

    private Entities extract(
            String raw
    ) {
        NormalizedText text =
                normalizer.normalize(raw);

        return extractor.extract(
                text,
                TODAY
        );
    }

    // ------------------------------------------------------------------
    // Ca tích hợp (plan yêu cầu)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("đặt gym ngày mai 7h tối ở q7")
    void fullSentence() {
        Entities entities =
                extract("đặt gym ngày mai 7h tối ở q7");

        assertEquals(
                "dat gym ngay mai 7h toi o q7",
                normalizer.normalize("đặt gym ngày mai 7h tối ở q7")
                        .plain()
        );

        assertEquals(
                Set.of(ServiceCode.GYM),
                entities.services()
        );

        assertEquals(
                2L,
                entities.branchId(),
                "q7 là chi nhánh id 2"
        );

        assertEquals(
                LocalDate.of(2026, 10, 4),
                entities.date()
        );

        assertEquals(
                LocalTime.of(19, 0),
                entities.time()
        );
    }

    @Test
    @DisplayName("thứ 2 lúc 6h chiều → 2026-10-05 18:00")
    void nextMonday() {
        Entities entities =
                extract("thứ 2 lúc 6h chiều");

        assertEquals(
                LocalDate.of(2026, 10, 5),
                entities.date()
        );

        assertEquals(
                LocalTime.of(18, 0),
                entities.time()
        );
    }

    @Test
    @DisplayName("gói boxing dưới 600k")
    void planWithMoney() {
        Entities entities =
                extract("gói boxing dưới 600k");

        assertEquals(
                Set.of(ServiceCode.BOXING),
                entities.services()
        );

        assertEquals(
                new BigDecimal("600000"),
                entities.money()
        );
    }

    @Test
    @DisplayName("5/10 → 2026-10-05")
    void numericDate() {
        assertEquals(
                LocalDate.of(2026, 10, 5),
                extract("ngày 5/10")
                        .date()
        );
    }

    @Test
    @DisplayName("q7 không bị nhầm với số")
    void branchNotNumber() {
        Entities entities =
                extract("hỏi về q7");

        assertEquals(
                2L,
                entities.branchId()
        );

        assertNull(
                entities.date(),
                "'q7' không được parse thành ngày"
        );

        assertNull(
                entities.money()
        );
    }

    // ------------------------------------------------------------------
    // Dịch vụ
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource({
            "gym,             GYM",
            "tập gym,        GYM",
            "phòng gym,      GYM",
            "tập tại,        GYM",
            "boxing,          BOXING",
            "đấm bốc,        BOXING",
            "quyền anh,      BOXING",
            "pickleball,     PICKLEBALL",
            "pickle ball,    PICKLEBALL",
            "sân pickleball, PICKLEBALL"
    })
    @DisplayName("Nhận diện dịch vụ")
    void services(
            String raw,
            ServiceCode expected
    ) {
        assertEquals(
                java.util.Set.of(expected),
                extract(raw).services()
        );
    }

    @Test
    @DisplayName("Cụm dài thắng cụm ngắn: 'tập gym' → GYM, không tách nhầm")
    void longestMatchWins() {
        Entities entities =
                extract("tập gym ở q1");

        assertEquals(
                java.util.Set.of(ServiceCode.GYM),
                entities.services()
        );

        assertEquals(
                1L,
                entities.branchId()
        );

        assertEquals(
                1,
                entities.spans()
                        .stream()
                        .filter(span ->
                                span.type()
                                        == EntityType.SERVICE)
                        .count(),
                "Chỉ được có 1 span SERVICE"
        );
    }

    @Test
    @DisplayName("Nhiều dịch vụ trong một câu")
    void multipleServices() {
        assertEquals(
                java.util.Set.of(
                        ServiceCode.GYM,
                        ServiceCode.PICKLEBALL
                ),
                extract("gói nào có gym và pickleball?")
                        .services()
        );
    }

    // ------------------------------------------------------------------
    // Tier / branch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Tier basic/premium/vip")
    void tiers() {
        assertEquals(
                PlanTier.BASIC,
                extract("gói basic giá bao nhiêu")
                        .tier()
        );

        assertEquals(
                PlanTier.PREMIUM,
                extract("gói premium")
                        .tier()
        );

        assertEquals(
                PlanTier.PREMIUM,
                extract("gói vip")
                        .tier()
        );
    }

    @Test
    @DisplayName("Chi nhánh theo code / tên / dạng rút gọn")
    void branches() {
        assertEquals(
                1L,
                extract("chi nhánh Q1 mở cửa lúc mấy giờ")
                        .branchId()
        );

        assertEquals(
                2L,
                extract("chi nhánh quận 7")
                        .branchId()
        );

        assertEquals(
                4L,
                extract("bình thạnh có pickleball không")
                        .branchId()
        );

        assertEquals(
                4L,
                extract("chi nhánh bình thạnh")
                        .branchId()
        );
    }

    @Test
    @DisplayName("Chi nhánh không tồn tại → null, không bịa")
    void unknownBranch() {
        assertNull(
                extract("chi nhánh Q9")
                        .branchId()
        );
    }

    // ------------------------------------------------------------------
    // Tiền
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @MethodSource("moneySamples")
    @DisplayName("Parse tiền VND")
    void money(
            String raw,
            long expected
    ) {
        assertEquals(
                new BigDecimal(expected),
                extract(raw).money(),
                raw
        );
    }

    static Stream<Arguments> moneySamples() {
        return Stream.of(
                Arguments.of("500k", 500_000L),
                Arguments.of("500.000", 500_000L),
                Arguments.of("1tr", 1_000_000L),
                Arguments.of("1,5tr", 1_500_000L),
                Arguments.of("1.5 triệu", 1_500_000L),
                Arguments.of("1,5 triệu", 1_500_000L),
                Arguments.of("500 nghìn", 500_000L),
                Arguments.of("2 triệu", 2_000_000L),
                Arguments.of("1.000.000", 1_000_000L),
                Arguments.of("600000", 600_000L),
                Arguments.of("dưới 600k", 600_000L),
                Arguments.of("khoảng 2 triệu rưỡi".replace(
                        "rưỡi",
                        "5"
                ), 2_500_000L)
        );
    }

    @Test
    @DisplayName("'1 trieu 5' = 1.500.000")
    void moneyShorthand() {
        assertEquals(
                new BigDecimal("1500000"),
                extract("1 triệu 5")
                        .money()
        );
    }

    // ------------------------------------------------------------------
    // Thời hạn
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1/3/6 tháng, 1 năm → số ngày")
    void duration() {
        assertEquals(
                30,
                extract("gói 1 tháng")
                        .durationDays()
        );

        assertEquals(
                90,
                extract("gói 3 tháng")
                        .durationDays()
        );

        assertEquals(
                180,
                extract("gói 6 tháng")
                        .durationDays()
        );

        assertEquals(
                365,
                extract("gói 1 năm")
                        .durationDays()
        );
    }

    // ------------------------------------------------------------------
    // Mã lịch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mã BOOK_ được nhận diện")
    void bookingCode() {
        String code =
                "BOOK_0123456789ABCDEF";

        assertEquals(
                code,
                extract("hủy lịch " + code)
                        .bookingCode()
        );
    }

    // ------------------------------------------------------------------
    // Span
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Span không chồng lấn và sắp theo vị trí")
    void spansDoNotOverlap() {
        Entities entities =
                extract("đặt gym ngày mai 7h tối ở q7 600k");

        int previousEnd =
                -1;

        for (Span span : entities.spans()) {

            assertTrue(
                    span.start() >= previousEnd,
                    "Span chồng lấn tại " + span
            );

            previousEnd =
                    span.end();
        }

        assertTrue(
                entities.spans()
                        .size()
                        >= 4
        );
    }

    @Test
    @DisplayName("Sản phẩm được nhận diện theo tên")
    void product() {
        assertEquals(
                "WATER500",
                extract("có nước suối không")
                        .productSku()
        );
    }

    @Test
    @DisplayName("Câu rỗng → không ném lỗi, entity rỗng")
    void emptyInput() {
        Entities entities =
                extract("");

        assertNotNull(
                entities.spans()
        );

        assertTrue(
                entities.spans()
                        .isEmpty()
        );

        assertTrue(
                !entities.hasEntity()
        );
    }

    @Test
    @DisplayName("hasRange() phản ánh đúng khoảng báo cáo")
    void range() {
        assertTrue(
                extract("doanh thu tháng này")
                        .hasRange()
        );

        assertTrue(
                !extract("gói nào rẻ nhất")
                        .hasRange()
        );
    }

    /** Gazetteer giả — không cần database. */
    static class FakeGazetteer
            implements GazetteerProvider {

        @Override
        public Map<String, Long> branchAliases() {
            return Map.ofEntries(
                    Map.entry("q1", 1L),
                    Map.entry("quan 1", 1L),
                    Map.entry("chi nhanh q1", 1L),
                    Map.entry("q7", 2L),
                    Map.entry("quan 7", 2L),
                    Map.entry("chi nhanh q7", 2L),
                    Map.entry("td", 3L),
                    Map.entry("thu duc", 3L),
                    Map.entry("chi nhanh thu duc", 3L),
                    Map.entry("bt", 4L),
                    Map.entry("binh thanh", 4L),
                    Map.entry("chi nhanh binh thanh", 4L)
            );
        }

        @Override
        public Map<String, String> productAliases() {
            return Map.of(
                    "nuoc suoi", "WATER500",
                    "nuoc dien giai", "ELECTRO",
                    "protein bar", "PROBAR"
            );
        }

    }

}