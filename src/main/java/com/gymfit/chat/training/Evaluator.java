package com.gymfit.chat.training;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tính accuracy, macro-F1, precision/recall/F1 từng intent và các cặp nhầm lẫn.
 *
 * <p>V2-2: thêm đọc breakdown theo {@code tier} (EASY/NATURAL/ADVERSARIAL),
 * theo {@code tag}, xuất ma trận nhầm lẫn {@code CSV} và danh sách câu sai
 * kèm dự đoán (mục 7.2 của plan mở rộng).
 */
public final class Evaluator {

    /**
     * Dự đoán cho một mẫu.
     *
     * @param text       câu gốc (để in danh sách câu sai; {@code null} với
     *                   tập val/test sinh tự động)
     * @param predicted  nhãn mô hình dự đoán
     * @param actual     nhãn thật
     * @param confidence độ tin cậy
     * @param tier       EASY / NATURAL / ADVERSARIAL ({@code null} nếu không có)
     * @param tags       các tag đo được ({@code question}, {@code short}…)
     */
    public record Prediction(
            String text,
            String predicted,
            String actual,
            double confidence,
            String tier,
            List<String> tags
    ) {

        /** Constructor ngắn cho tập val/test (không có tier/tag). */
        public Prediction(
                String predicted,
                String actual,
                double confidence
        ) {
            this(
                    null,
                    predicted,
                    actual,
                    confidence,
                    null,
                    List.of()
            );
        }

        /** Constructor cho holdout v2 (có tier/tag, chưa có text). */
        public Prediction(
                String predicted,
                String actual,
                double confidence,
                String tier,
                List<String> tags
        ) {
            this(
                    null,
                    predicted,
                    actual,
                    confidence,
                    tier,
                    tags
            );
        }
    }

    /**
     * Kết quả đánh giá.
     *
     * @param accuracy      độ chính xác tổng
     * @param macroF1        F1 trung bình macro
     * @param perIntent      precision/recall/f1/support theo intent
     * @param confusions     top cặp (actual → predicted) sai
     * @param outOfScopeRecall recall của OUT_OF_SCOPE
     * @param samples        tổng số mẫu đã đánh giá
     */
    public record Result(
            double accuracy,
            double macroF1,
            Map<String, double[]> perIntent,
            List<Map.Entry<String, Integer>> confusions,
            double outOfScopeRecall,
            int samples
    ) {
    }

    private Evaluator() {
    }

    public static Result evaluate(
            List<Prediction> predictions
    ) {
        int total =
                predictions.size();

        int correct =
                0;

        Map<String, int[]> stats =
                new LinkedHashMap<>();

        Map<String, Map<String, Integer>> confusion =
                new LinkedHashMap<>();

        for (Prediction prediction : predictions) {

            String actual =
                    prediction.actual();

            String predicted =
                    prediction.predicted();

            // [tp, fp, fn]
            stats.computeIfAbsent(
                    actual,
                            key -> new int[3]
            );

            stats.computeIfAbsent(
                    predicted,
                            key -> new int[3]
            );

            if (actual.equals(predicted)) {
                correct++;

                stats.get(actual)[0]++;

            } else {

                stats.get(predicted)[1]++;
                stats.get(actual)[2]++;

                confusion.computeIfAbsent(
                                actual,
                                key -> new LinkedHashMap<>()
                        )
                        .merge(
                                predicted,
                                1,
                                Integer::sum
                        );
            }
        }

        Map<String, double[]> perIntent =
                new LinkedHashMap<>();

        double f1Sum =
                0;

        int counted =
                0;

        for (Map.Entry<String, int[]> entry
                : stats.entrySet()) {

            int tp =
                    entry.getValue()[0];

            int fp =
                    entry.getValue()[1];

            int fn =
                    entry.getValue()[2];

            double precision =
                    tp + fp == 0
                            ? 0
                            : (double) tp / (tp + fp);

            double recall =
                    tp + fn == 0
                            ? 0
                            : (double) tp / (tp + fn);

            double f1 =
                    precision + recall == 0
                            ? 0
                            : 2 * precision * recall
                            / (precision + recall);

            perIntent.put(
                    entry.getKey(),
                    new double[]{
                            precision,
                            recall,
                            f1
                    }
            );

            if (tp + fn > 0) {
                f1Sum += f1;

                counted++;
            }
        }

        List<Map.Entry<String, Integer>> confusions =
                new ArrayList<>();

        confusion.forEach((actual, map) ->
                map.forEach((predicted, count) ->
                        confusions.add(
                                Map.entry(
                                        actual
                                                + " → "
                                                + predicted,
                                        count
                                )
                        )
                )
        );

        confusions.sort(
                (a, b) ->
                        Integer.compare(
                                b.getValue(),
                                a.getValue()
                        )
        );

        double[] outOfScope =
                perIntent.get("OUT_OF_SCOPE");

        return new Result(
                total == 0
                        ? 0
                        : (double) correct / total,
                counted == 0
                        ? 0
                        : f1Sum / counted,
                perIntent,
                confusions,
                outOfScope == null
                        ? 0
                        : outOfScope[1],
                total
        );
    }

    // ------------------------------------------------------------------
    // V2-2: breakdown theo tier / tag, CSV, danh sách câu sai
    // ------------------------------------------------------------------

    /**
     * Đánh giá riêng từng {@code tier} (EASY / NATURAL / ADVERSARIAL).
     * Mẫu không có tier bị bỏ qua.
     */
    public static Map<String, Result> byTier(
            List<Prediction> predictions
    ) {
        return groupBy(
                predictions,
                prediction ->
                        prediction.tier() == null
                                || prediction.tier().isBlank()
                                ? List.of()
                                : List.of(prediction.tier())
        );
    }

    /**
     * Đánh giá riêng từng {@code tag}. Một mẫu mang nhiều tag thì được tính
     * vào tất cả nhóm tag của nó (nên tổng các nhóm có thể &gt; tổng mẫu).
     */
    public static Map<String, Result> byTag(
            List<Prediction> predictions
    ) {
        return groupBy(
                predictions,
                prediction ->
                        prediction.tags() == null
                                ? List.of()
                                : prediction.tags()
        );
    }

    private static Map<String, Result> groupBy(
            List<Prediction> predictions,
            java.util.function.Function<Prediction, List<String>> keyOf
    ) {

        Map<String, List<Prediction>> groups =
                new java.util.TreeMap<>();

        for (Prediction prediction : predictions) {

            for (String key : keyOf.apply(prediction)) {

                if (key == null || key.isBlank()) {
                    continue;
                }

                groups.computeIfAbsent(
                                key,
                                ignored -> new ArrayList<>()
                        )
                        .add(prediction);
            }
        }

        Map<String, Result> results =
                new LinkedHashMap<>();

        groups.forEach((key, group) ->
                results.put(
                        key,
                        evaluate(group)
                )
        );

        return results;
    }

    /**
     * Ma trận nhầm lẫn đầy đủ (gồm cả ô đúng) dạng CSV –
     * ghi ra {@code docs/chatbot/confusion.csv}.
     */
    public static String confusionCsv(
            List<Prediction> predictions
    ) {

        Map<String, Map<String, Integer>> matrix =
                new java.util.TreeMap<>();

        for (Prediction prediction : predictions) {

            matrix.computeIfAbsent(
                            prediction.actual(),
                            ignored -> new java.util.TreeMap<>()
                    )
                    .merge(
                            prediction.predicted(),
                            1,
                            Integer::sum
                    );
        }

        StringBuilder builder =
                new StringBuilder("actual,predicted,count\n");

        matrix.forEach((actual, row) ->
                row.forEach((predicted, count) ->
                        builder.append(actual)
                                .append(',')
                                .append(predicted)
                                .append(',')
                                .append(count)
                                .append('\n')
                )
        );

        return builder.toString();
    }

    /**
     * Danh sách câu sai kèm dự đoán – dùng để chẩn đoán từng câu trong
     * báo cáo huấn luyện.
     */
    public static List<String> wrongPredictions(
            List<Prediction> predictions
    ) {

        return predictions.stream()
                .filter(prediction ->
                        !prediction.actual()
                                .equals(prediction.predicted())
                )
                .map(prediction ->
                        (prediction.text() == null
                                ? "(không có text)"
                                : prediction.text())
                                + " → " + prediction.predicted()
                                + " (cần " + prediction.actual() + ")"
                )
                .toList();
    }

    /** In bảng breakdown (tier / tag) cho báo cáo Markdown. */
    public static String toMarkdown(
            String title,
            Map<String, Result> groups
    ) {

        StringBuilder builder =
                new StringBuilder();

        builder.append("### ")
                .append(title)
                .append("\n\n");

        if (groups.isEmpty()) {
            builder.append("_Không có dữ liệu._\n\n");
            return builder.toString();
        }

        builder.append(
                "| Nhóm | Số mẫu | Accuracy | Macro-F1 | Sai |\n"
                        + "|---|---:|---:|---:|---:|\n"
        );

        groups.forEach((group, result) -> {

            int samples =
                    result.samples();

            builder.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %d | %.4f | %.4f | %d |%n",
                            group,
                            samples,
                            result.accuracy(),
                            result.macroF1(),
                            samples
                                    - (int) Math.round(
                                    result.accuracy() * samples
                            )
                    )
            );
        });

        builder.append('\n');

        return builder.toString();
    }

    public static String toMarkdown(
            String title,
            Result result
    ) {
        StringBuilder sb =
                new StringBuilder();

        sb.append("### ")
                .append(title)
                .append("\n\n");

        sb.append(
                String.format(
                        Locale.ROOT,
                        "- Accuracy: **%.4f**%n"
                                + "- Macro-F1: **%.4f**%n"
                                + "- Recall OUT_OF_SCOPE: **%.4f**%n"
                                + "- Số mẫu: %d intent có nhãn thật%n%n",
                        result.accuracy(),
                        result.macroF1(),
                        result.outOfScopeRecall(),
                        result.perIntent()
                                .size()
                )
        );

        sb.append(
                "| Intent | Precision | Recall | F1 |\n"
                        + "|---|---|---|---|\n"
        );

        result.perIntent()
                .entrySet()
                .stream()
                .sorted(
                        Map.Entry.comparingByKey()
                )
                .forEach(entry -> {

                    double[] v =
                            entry.getValue();

                    sb.append(
                            String.format(
                                    Locale.ROOT,
                                    "| %s | %.4f | %.4f | %.4f |%n",
                                    entry.getKey(),
                                    v[0],
                                    v[1],
                                    v[2]
                            )
                    );
                });

        sb.append("\n**Top cặp nhầm lẫn**\n\n");

        result.confusions()
                .stream()
                .limit(10)
                .forEach(entry ->
                        sb.append(
                                String.format(
                                        Locale.ROOT,
                                        "- %s: %d%n",
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                );

        return sb.toString();
    }

}