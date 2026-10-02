package com.gymfit.chat.training;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tính accuracy, macro-F1, precision/recall/F1 từng intent và các cặp nhầm lẫn.
 */
public final class Evaluator {

    /** Dự đoán cho một mẫu. */
    public record Prediction(
            String predicted,
            String actual,
            double confidence
    ) {
    }

    /**
     * Kết quả đánh giá.
     *
     * @param accuracy      độ chính xác tổng
     * @param macroF1        F1 trung bình macro
     * @param perIntent      precision/recall/f1/support theo intent
     * @param confusions     top cặp (actual → predicted) sai
     * @param outOfScopeRecall recall của OUT_OF_SCOPE
     */
    public record Result(
            double accuracy,
            double macroF1,
            Map<String, double[]> perIntent,
            List<Map.Entry<String, Integer>> confusions,
            double outOfScopeRecall
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
                        : outOfScope[1]
        );
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