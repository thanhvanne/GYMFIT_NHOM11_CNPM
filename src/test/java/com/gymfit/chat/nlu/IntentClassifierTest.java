package com.gymfit.chat.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import com.gymfit.chat.training.Evaluator;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String HOLDOUT_V2 =
            "src/main/resources/chatbot/holdout_v2.jsonl";

    private static final String HOLDOUT_LEGACY =
            "src/main/resources/chatbot/holdout.jsonl";

    /** Một dòng holdout (schema V2 nếu có, V1 nếu thiếu trường). */
    private record HoldoutRow(
            String text,
            String intent,
            String tier,
            List<String> tags
    ) {
    }

    /**
     * Nạp holdout: ưu tiên {@code holdout_v2.jsonl} (schema
     * {@code text,intent,tier,tags,role}), thiếu thì dùng {@code holdout.jsonl}.
     */
    private static List<HoldoutRow> holdout() throws Exception {

        java.nio.file.Path path =
                java.nio.file.Path.of(HOLDOUT_V2);

        if (!Files.exists(path)) {
            path = java.nio.file.Path.of(HOLDOUT_LEGACY);
        }

        assertTrue(
                Files.exists(path),
                "Không tìm thấy holdout: " + path
        );

        ObjectMapper mapper =
                new ObjectMapper();

        List<HoldoutRow> rows =
                new ArrayList<>();

        for (String line : Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        )) {

            if (line.isBlank()) {
                continue;
            }

            JsonNode node =
                    mapper.readTree(line);

            List<String> tags =
                    new ArrayList<>();

            node.path("tags")
                    .forEach(tag ->
                            tags.add(
                                    tag.asText()
                            )
                    );

            rows.add(
                    new HoldoutRow(
                            node.path("text")
                                    .asText(),
                            node.path("intent")
                                    .asText(),
                            node.path("tier")
                                    .asText(""),
                            tags
                    )
            );
        }

        assertFalse(
                rows.isEmpty(),
                "Holdout rỗng: " + path
        );

        return rows;
    }

    /** Chạy mô hình trên toàn bộ holdout, trả về dự đoán kèm tier/tags. */
    private List<Evaluator.Prediction> holdoutPredictions(
            List<HoldoutRow> rows
    ) {

        List<Evaluator.Prediction> predictions =
                new ArrayList<>();

        for (HoldoutRow row : rows) {

            IntentPrediction prediction =
                    classify(row.text());

            predictions.add(
                    new Evaluator.Prediction(
                            row.text(),
                            prediction.intent().name(),
                            row.intent(),
                            prediction.confidence(),
                            row.tier(),
                            row.tags()
                    )
            );
        }

        return predictions;
    }

    @Test
    @DisplayName("Accuracy trên holdout ≥ 0.85 (fail build nếu không)")
    void holdoutAccuracy() throws Exception {

        List<Evaluator.Prediction> predictions =
                holdoutPredictions(
                        holdout()
                );

        Evaluator.Result result =
                Evaluator.evaluate(
                        predictions
                );

        List<String> wrong =
                Evaluator.wrongPredictions(
                        predictions
                );

        assertTrue(
                result.accuracy() >= 0.85,
                "Accuracy holdout = "
                        + result.accuracy()
                        + " (< 0.85). Sai: "
                        + wrong
        );
    }

    // ------------------------------------------------------------------
    // Cổng chất lượng V2 (mục 7.2) — V2-2 CHỈ CẢNH BÁO
    // ------------------------------------------------------------------

    private static final double GATE_ACCURACY = 0.92;
    private static final double GATE_MACRO_F1 = 0.90;
    private static final double GATE_MIN_F1 = 0.80;
    private static final double GATE_OOS_RECALL = 0.92;
    private static final double GATE_OOS_PRECISION = 0.88;
    private static final double GATE_NATURAL_FALLBACK = 0.12;

    /** Ngưỡng fallback của {@code DialogueManager} (application.yml). */
    private static final double THRESHOLD_CLARIFY = 0.40;

    @Test
    @DisplayName("Bảng cổng V2 (mục 7.2) — V2-2 chỉ in cảnh báo, fail build từ V2-6")
    void gateTableV2() throws Exception {

        List<HoldoutRow> rows =
                holdout();

        List<Evaluator.Prediction> predictions =
                holdoutPredictions(
                        rows
                );

        Evaluator.Result result =
                Evaluator.evaluate(
                        predictions
                );

        java.util.Set<String> actualIntents =
                rows.stream()
                        .map(HoldoutRow::intent)
                        .collect(
                                java.util.stream.Collectors.toSet()
                        );

        double minF1 =
                actualIntents.stream()
                        .mapToInt(intent -> {
                            double[] values =
                                    result.perIntent()
                                            .get(intent);
                            return values == null
                                    ? 0
                                    : (int) Math.round(values[2] * 10000);
                        })
                        .min()
                        .orElse(0) / 10000.0;

        double[] oos =
                result.perIntent()
                        .get("OUT_OF_SCOPE");

        double oosRecall =
                oos == null ? 0 : oos[1];

        double oosPrecision =
                oos == null ? 0 : oos[0];

        int naturalTotal = 0;
        int naturalFallback = 0;

        for (int index = 0;
             index < rows.size();
             index++) {

            HoldoutRow row =
                    rows.get(index);

            if (!"NATURAL".equals(row.tier())) {
                continue;
            }

            naturalTotal++;

            Evaluator.Prediction prediction =
                    predictions.get(index);

            boolean fallback =
                    prediction.confidence() < THRESHOLD_CLARIFY
                            || ("OUT_OF_SCOPE".equals(
                            prediction.predicted()
                    )
                            && !"OUT_OF_SCOPE".equals(
                            row.intent()
                    ));

            if (fallback) {
                naturalFallback++;
            }
        }

        double naturalFallbackRate =
                naturalTotal == 0
                        ? 0
                        : (double) naturalFallback / naturalTotal;

        StringBuilder table =
                new StringBuilder();

        table.append(
                "\n=== CỔNG CHẤT LƯỢNG V2 (mục 7.2) — "
                        + "CHỈ CẢNH BÁO Ở V2-2, FAIL BUILD TỪ V2-6 ===\n"
        );
        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "Holdout: %d câu ("
                                + "EASY=%d, NATURAL=%d, ADVERSARIAL=%d)%n",
                        result.samples(),
                        rows.stream()
                                .filter(r -> "EASY".equals(r.tier()))
                                .count(),
                        rows.stream()
                                .filter(r -> "NATURAL".equals(r.tier()))
                                .count(),
                        rows.stream()
                                .filter(r -> "ADVERSARIAL".equals(r.tier()))
                                .count()
                )
        );
        table.append(
                "----------------------------------------------------------------\n"
        );
        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8s %8s %s%n",
                        "Chỉ số",
                        "Hiện tại",
                        "Cổng",
                        "Kết quả"
                )
        );

        appendGate(
                table,
                "Intent accuracy",
                result.accuracy(),
                GATE_ACCURACY,
                ">="
        );
        appendGate(
                table,
                "Macro-F1",
                result.macroF1(),
                GATE_MACRO_F1,
                ">="
        );
        appendGate(
                table,
                "F1 thấp nhất (trên các intent có thật)",
                minF1,
                GATE_MIN_F1,
                ">="
        );
        appendGate(
                table,
                "Recall OUT_OF_SCOPE",
                oosRecall,
                GATE_OOS_RECALL,
                ">="
        );
        appendGate(
                table,
                "Precision OUT_OF_SCOPE",
                oosPrecision,
                GATE_OOS_PRECISION,
                ">="
        );
        appendGate(
                table,
                "Fallback trên câu tier NATURAL ("
                        + naturalFallback + "/" + naturalTotal + ")",
                naturalFallbackRate,
                GATE_NATURAL_FALLBACK,
                "<="
        );

        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8s %8s %s%n",
                        "Câu ghép (tag compound)",
                        "—",
                        ">=0.80",
                        "CHƯA ĐO"
                )
        );
        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8s %8s %s%n",
                        "Entity/slot F1",
                        "—",
                        ">=0.93",
                        "CHƯA ĐO"
                )
        );
        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8s %8s %s%n",
                        "FAQ recall@1 / recall@3",
                        "—",
                        "0.85/0.95",
                        "CHƯA ĐO (V2-3)"
                )
        );
        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8s %8s %s%n",
                        "Task success (kịch bản)",
                        "—",
                        ">=0.88",
                        "CHƯA ĐO (từ 250 kịch bản)"
                )
        );
        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8s %8s %s%n",
                        "Độ trễ p95 không tính DB",
                        "110.6ms(có DB)",
                        "<300ms",
                        "CHƯA ĐO (V2-16)"
                )
        );

        table.append(
                "----------------------------------------------------------------\n"
        );

        System.out.println(table);

        // V2-2: KHÔNG fail build – giữ lại assert tối thiểu để test có ý nghĩa.
        assertTrue(
                result.samples() > 0,
                "Holdout không có mẫu nào được đánh giá"
        );
    }

    private static void appendGate(
            StringBuilder table,
            String name,
            double actual,
            double gate,
            String direction
    ) {

        boolean pass =
                ">=".equals(direction)
                        ? actual >= gate
                        : actual <= gate;

        table.append(
                String.format(
                        java.util.Locale.ROOT,
                        "%-46s %8.4f %8.2f %s%n",
                        name,
                        actual,
                        gate,
                        pass ? "ĐẠT" : "CHƯA ĐẠT"
                )
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