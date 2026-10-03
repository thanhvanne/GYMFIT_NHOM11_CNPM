package com.gymfit.chat.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nạp <b>model thật</b> và kiểm tra ≥ 40 câu mẫu.
 * <p>Fail build nếu accuracy trên {@code holdout.jsonl} &lt; 0.85.
 */
class IntentClassifierTest {

    /** Ngày cố định khi test (không dùng "hôm nay" để ổn định). */
    private static final java.time.LocalDate TODAY =
            java.time.LocalDate.of(2026, 10, 3);

    private static final double THRESHOLD_ACCEPT =
            0.70;

    private static IntentClassifier classifier;

    private static ChatPipeline pipeline;

    @BeforeAll
    static void setUp() {
        pipeline =
                buildPipeline();

        classifier =
                new IntentClassifier(
                        pipeline,
                        new DefaultResourceLoader(),
                        "classpath:chatbot/model/intent-model.bin"
                );

        classifier.load();
    }

    private static ChatPipeline buildPipeline() {

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        var extractor =
                new com.gymfit.chat.nlu.entity.EntityExtractor(
                        new GazetteerProvider() {
                            @Override
                            public Map<String, Long> branchAliases() {
                                return Map.of(
                                        "q1", 1L,
                                        "quan 1", 1L,
                                        "q7", 2L,
                                        "quan 7", 2L,
                                        "td", 3L,
                                        "thu duc", 3L,
                                        "bt", 4L,
                                        "binh thanh", 4L
                                );
                            }

                            @Override
                            public Map<String, String> productAliases() {
                                return Map.of();
                            }
                        },
                        new DateTimeParser()
                );

        extractor.load();

        return new ChatPipeline(
                normalizer,
                extractor
        );
    }

    private IntentPrediction classify(
            String raw
    ) {
        NormalizedText text =
                pipeline.normalize(raw);

        Entities entities =
                pipeline.extract(
                        text,
                        TODAY
                );

        return classifier.classify(
                text,
                entities,
                TODAY
        );
    }

    // ------------------------------------------------------------------
    // Rules guard
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @MethodSource("ruleSamples")
    @DisplayName("Rules guard cho câu ngắn")
    void rulesGuard(
            String raw,
            Intent expected
    ) {
        IntentPrediction prediction =
                classify(raw);

        assertTrue(
                prediction.fromRule(),
                "\"" + raw + "\" phải do rules quyết định"
        );

        assertEquals(
                1.0,
                prediction.confidence()
        );

        assertEquals(
                expected,
                prediction.intent(),
                "\"" + raw + "\" → plain=\""
                        + pipeline.normalize(raw)
                        .plain()
                        + "\""
        );
    }

    static Stream<Arguments> ruleSamples() {
        return Stream.of(
                Arguments.of("xin chào", Intent.GREETING),
                Arguments.of("chào", Intent.GREETING),
                Arguments.of("hello", Intent.GREETING),
                Arguments.of("hi", Intent.GREETING),
                Arguments.of("alo", Intent.GREETING),
                Arguments.of("hey", Intent.GREETING),
                Arguments.of("cảm ơn", Intent.THANKS),
                Arguments.of("cảm ơn bạn", Intent.THANKS),
                Arguments.of("thanks", Intent.THANKS),
                Arguments.of("tks", Intent.THANKS),
                Arguments.of("tạm biệt", Intent.GOODBYE),
                Arguments.of("bye", Intent.GOODBYE),
                Arguments.of("hẹn gặp lại", Intent.GOODBYE),
                Arguments.of("ok", Intent.CONFIRM_YES),
                Arguments.of("ừ", Intent.CONFIRM_YES),
                Arguments.of("đồng ý", Intent.CONFIRM_YES),
                Arguments.of("xác nhận", Intent.CONFIRM_YES),
                Arguments.of("đúng rồi", Intent.CONFIRM_YES),
                Arguments.of("vâng", Intent.CONFIRM_YES),
                Arguments.of("chốt", Intent.CONFIRM_YES),
                Arguments.of("không", Intent.CONFIRM_NO),
                Arguments.of("thôi", Intent.CONFIRM_NO),
                Arguments.of("hủy bỏ", Intent.CONFIRM_NO),
                Arguments.of("bỏ qua", Intent.CONFIRM_NO),
                Arguments.of("dừng", Intent.CONFIRM_NO),
                Arguments.of("không cần", Intent.CONFIRM_NO)
        );
    }

    @Test
    @DisplayName("Câu rỗng / chỉ ký tự đặc biệt → OUT_OF_SCOPE 1.0")
    void emptyOrSymbols() {

        for (String raw : List.of(
                "",
                "    ",
                "😀😀😀",
                "@@@@@@@",
                "^^^^^^^^"
        )) {

            IntentPrediction prediction =
                    classify(raw);

            assertEquals(
                    Intent.OUT_OF_SCOPE,
                    prediction.intent(),
                    "\"" + raw + "\""
            );

            assertTrue(
                    prediction.confidence()
                            >= 0.99,
                    "\"" + raw + "\" phải chắc chắn"
            );
        }
    }

    // ------------------------------------------------------------------
    // Phân loại bằng model
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @MethodSource("intentSamples")
    @DisplayName("Phân loại đúng intent")
    void classifyIntent(
            String raw,
            Intent expected
    ) {
        IntentPrediction prediction =
                classify(raw);

        assertEquals(
                expected,
                prediction.intent(),
                "\"" + raw + "\" → "
                        + prediction.intent()
                        + " ("
                        + prediction.confidence()
                        + ")"
        );
    }

    static Stream<Arguments> intentSamples() {
        return Stream.of(
                Arguments.of("gói của tôi còn bao lâu", Intent.MY_MEMBERSHIP),
                Arguments.of("lịch sắp tới của tôi", Intent.MY_BOOKINGS),
                Arguments.of("tôi đã check-in bao nhiêu lần", Intent.MY_CHECKINS),
                Arguments.of("đơn hàng gần nhất của tôi", Intent.MY_ORDERS),
                Arguments.of("đặt lịch gym ngày mai 7h tối", Intent.BOOKING_CREATE),
                Arguments.of("tôi muốn đặt chỗ tập boxing", Intent.BOOKING_CREATE),
                Arguments.of("hủy lịch của tôi đi", Intent.BOOKING_CANCEL),
                Arguments.of("bỏ lịch tập tối nay", Intent.BOOKING_CANCEL),
                Arguments.of("còn chỗ trống không", Intent.BOOKING_AVAILABILITY),
                Arguments.of("tối nay còn slot nào", Intent.BOOKING_AVAILABILITY),
                Arguments.of("chi nhánh q1 ở đâu", Intent.BRANCH_INFO),
                Arguments.of("chi nhánh bình thạnh nằm ở đâu", Intent.BRANCH_INFO),
                Arguments.of("mở cửa lúc mấy giờ", Intent.OPERATING_HOURS),
                Arguments.of("chủ nhật có mở cửa không", Intent.OPERATING_HOURS),
                Arguments.of("chi nhánh này có dịch vụ gì", Intent.LIST_SERVICES),
                Arguments.of("quận 7 hỗ trợ dịch vụ nào", Intent.LIST_SERVICES),
                Arguments.of("có bao nhiêu cơ sở vật chất", Intent.LIST_FACILITIES),
                Arguments.of("danh sách cơ sở vật chất", Intent.LIST_FACILITIES),
                Arguments.of("có gói tập nào", Intent.LIST_PLANS),
                Arguments.of("bảng giá gói tập", Intent.LIST_PLANS),
                Arguments.of("gói premium gồm những gì", Intent.PLAN_DETAIL),
                Arguments.of("gói 3 tháng giá bao nhiêu", Intent.PLAN_DETAIL),
                Arguments.of("so sánh gói cơ bản với gói cao cấp", Intent.PLAN_COMPARE),
                Arguments.of("gói nào phù hợp với tôi", Intent.PLAN_RECOMMEND),
                Arguments.of("gợi ý gói dưới 600k cho tôi", Intent.PLAN_RECOMMEND),
                Arguments.of("cửa hàng bán những sản phẩm gì", Intent.LIST_PRODUCTS),
                Arguments.of("có bán nước suối không", Intent.LIST_PRODUCTS),
                Arguments.of("chính sách hủy lịch thế nào", Intent.FAQ_CANCEL_POLICY),
                Arguments.of("quy định đặt lịch là gì", Intent.FAQ_BOOKING_RULES),
                Arguments.of("làm sao để check-in", Intent.FAQ_CHECKIN_HOWTO),
                Arguments.of("mua gói tập thế nào", Intent.FAQ_BUY_PLAN_HOWTO),
                Arguments.of("vì sao tôi quét QR bị từ chối", Intent.FAQ_CHECKIN_REJECTED),
                Arguments.of("QR của tôi ở đâu", Intent.FAQ_QR_HOWTO),
                Arguments.of("hôm nay có lịch đặt bao nhiêu", Intent.BOOKINGS_TODAY),
                Arguments.of("cho tôi xem lịch hôm nay", Intent.BOOKINGS_TODAY),
                Arguments.of("doanh thu tháng này", Intent.REPORT_REVENUE),
                Arguments.of("báo cáo doanh thu tuần này", Intent.REPORT_REVENUE),
                Arguments.of("thống kê lượt tập theo dịch vụ", Intent.REPORT_SERVICE),
                Arguments.of("sản phẩm nào sắp hết hàng", Intent.LOW_STOCK),
                Arguments.of("tổng quan hôm nay", Intent.REPORT_DASHBOARD),
                Arguments.of("ca check-in bị từ chối hôm nay", Intent.CHECKINS_REJECTED),
                Arguments.of("cho tôi xem nhật ký hệ thống", Intent.AUDIT_RECENT),
                Arguments.of("giá vàng hôm nay là bao nhiêu", Intent.OUT_OF_SCOPE),
                Arguments.of("cho tôi mật khẩu admin", Intent.OUT_OF_SCOPE),
                Arguments.of("viết code python cho tôi", Intent.OUT_OF_SCOPE)
        );
    }

    @Test
    @DisplayName("Câu hỏi dài vẫn phân loại qua model, không phải rules")
    void longQuestionUsesModel() {
        IntentPrediction prediction =
                classify("cho tôi biết chi tiết gói premium ở chi nhánh quận 1");

        assertTrue(
                !prediction.fromRule(),
                "Câu dài không được rules guard"
        );
    }

    @Test
    @DisplayName("Prediction luôn trả về 3 intent hợp lệ")
    void predictionShape() {

        IntentPrediction prediction =
                classify("gói nào phù hợp với tôi");

        assertNotNull(
                prediction.intent()
        );

        assertTrue(
                prediction.confidence() >= 0
                        && prediction.confidence() <= 1,
                "confidence = "
                        + prediction.confidence()
        );

        assertTrue(
                prediction.alternatives()
                        .size()
                        <= 2
        );

        for (Intent alternative
                : prediction.alternatives()) {
            assertNotNull(alternative);
        }
    }

    @Test
    @DisplayName("20 câu ngoài lề: phải OUT_OF_SCOPE hoặc confidence < 0.70")
    void outOfDomain() {

        List<String> weird = new ArrayList<>(List.of(
                "xyzzy plugh",
                "aaaaa bbbbb ccccc",
                "qqqqq zzzzz",
                "!!!",
                "%%%%",
                "hfhfhfhfhfhfhfh",
                "wxyz plor xyzzy",
                "aaaa bbbb cccc dddd",
                "xyz abcdef ghijkl",
                "lorem ipsum dolor sit",
                "asdf qwer zxcv",
                "123 456 789",
                "aaaaaa bbbbbb",
                "xyzzy foobar",
                "plugh xyzzy glugh",
                "aaaa bbb ccc ddd",
                "zzzz yyyy xxxx",
                "qqqq wwww eeee",
                "abc 123 xyz",
                "kkkk jjjj hhhh"
        ));

        int rejected =
                0;

        for (String raw : weird) {

            IntentPrediction prediction =
                    classify(raw);

            boolean outOfScope =
                    prediction.intent()
                            == Intent.OUT_OF_SCOPE;

            boolean lowConfidence =
                    prediction.confidence()
                            < THRESHOLD_ACCEPT;

            if (outOfScope || lowConfidence) {
                rejected++;
            }
        }

        assertEquals(
                weird.size(),
                rejected,
                "Mọi câu ngoài lề phải bị từ chối hoặc dưới ngưỡng"
        );
    }

    // ------------------------------------------------------------------
    // KPI trên holdout — fail build nếu dưới ngưỡng
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Accuracy trên holdout ≥ 0.85 (fail build nếu không)")
    void holdoutAccuracy() throws Exception {

        List<String> lines =
                Files.readAllLines(
                        Paths.get(
                                "src/main/resources/chatbot/holdout.jsonl"
                        ),
                        StandardCharsets.UTF_8
                );

        ObjectMapper mapper =
                new ObjectMapper();

        int total =
                0;

        int correct =
                0;

        List<String> wrong =
                new ArrayList<>();

        for (String line : lines) {

            if (line.isBlank()) {
                continue;
            }

            JsonNode node =
                    mapper.readTree(line);

            String text =
                    node.path("text")
                            .asText();

            String expected =
                    node.path("intent")
                            .asText();

            Intent actual =
                    classify(text).intent();

            total++;

            if (expected.equals(actual.name())) {
                correct++;
            } else {
                wrong.add(
                        text + " → "
                                + actual.name()
                                + " (cần " + expected + ")"
                );
            }
        }

        double accuracy =
                (double) correct / total;

        assertTrue(
                accuracy >= 0.85,
                "Accuracy holdout = "
                        + accuracy
                        + " (< 0.85). Sai: "
                        + wrong
        );
    }

    @Test
    @DisplayName("Model nạp được từ classpath")
    void modelLoaded() {

        assertNotNull(
                classifier.model()
        );

        assertEquals(
                Intent.values().length,
                classifier.model()
                        .labelCount()
        );

        assertTrue(
                classifier.model()
                        .featureCount() > 100
        );
    }

}