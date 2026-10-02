package com.gymfit.chat.nlu;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trích đặc trưng từ câu đã mask.
 *
 * <p>Đặc trưng: word unigram, word bigram, char n-gram (n=2..5) trên từng token
 * đã bọc biên {@code #token#}. Trọng số TF-IDF rồi L2-normalize.
 */
@Slf4j
public final class FeatureExtractor {

    public static final int MIN_DF = 2;
    public static final int MAX_VOCAB = 30_000;

    private static final int MIN_CHAR_N = 2;
    private static final int MAX_CHAR_N = 5;

    private FeatureExtractor() {
    }

    /** Danh sách feature của một câu (chưa trọng số). */
    public static List<String> features(
            String masked
    ) {
        List<String> features =
                new ArrayList<>();

        if (masked == null
                || masked.isBlank()) {
            return features;
        }

        String[] tokens =
                masked.trim()
                        .split("\\s+");

        // Word unigram
        for (String token : tokens) {
            features.add(
                    "w:"
                            + token
            );
        }

        // Word bigram
        for (int i = 0; i < tokens.length - 1; i++) {
            features.add(
                    "b:"
                            + tokens[i]
                            + "_"
                            + tokens[i + 1]
            );
        }

        // Char n-gram n=2..5 trên token bọc biên
        for (String token : tokens) {
            String padded =
                    "#" + token + "#";

            for (int n = MIN_CHAR_N;
                 n <= MAX_CHAR_N;
                 n++) {

                if (padded.length() < n) {
                    continue;
                }

                for (int i = 0; i + n <= padded.length(); i++) {
                    features.add(
                            "c:"
                                    + padded.substring(
                                            i,
                                            i + n
                                    )
                    );
                }
            }
        }

        return features;
    }

    /**
     * Thống kê document frequency và IDF.
     *
     * @return key → idf (float)
     */
    public static Map<String, Double> computeIdf(
            List<List<String>> docs
    ) {
        Map<String, Integer> df =
                new HashMap<>();

        int n =
                docs.size();

        for (List<String> doc : docs) {
            Set<String> unique =
                    new HashSet<>(doc);

            for (String key : unique) {
                df.merge(
                        key,
                        1,
                        Integer::sum
                );
            }
        }

        // Giữ tối đa MAX_VOCAB, ưu tiên df cao.
        List<Map.Entry<String, Integer>> sorted =
                new ArrayList<>(
                        df.entrySet()
                );

        sorted.sort(
                (a, b) -> {

                    int cmp =
                            Integer.compare(
                                    b.getValue(),
                                    a.getValue()
                            );

                    return cmp != 0
                            ? cmp
                            : a.getKey()
                            .compareTo(
                                    b.getKey()
                            );
                }
        );

        Map<String, Double> idf =
                new HashMap<>();

        int kept =
                0;

        for (Map.Entry<String, Integer> entry : sorted) {

            if (entry.getValue() < MIN_DF) {
                continue;
            }

            if (kept >= MAX_VOCAB) {
                break;
            }

            double value =
                    Math.log(
                            (n + 1.0)
                                    / (entry.getValue() + 1.0)
                    ) + 1.0;

            idf.put(
                    entry.getKey(),
                    value
            );

            kept++;
        }

        return idf;
    }

    /** TF = 1 + ln(count). */
    public static double tf(
            int count
    ) {
        if (count <= 0) {
            return 0;
        }

        return 1.0 + Math.log(count);
    }

    /** Chuẩn hoá L2 theo trọng số đã nhân idf. */
    public static void l2Normalize(
            Map<Integer, Double> vector
    ) {
        double sum =
                0;

        for (double v : vector.values()) {
            sum += v * v;
        }

        double norm =
                Math.sqrt(sum);

        if (norm <= 0) {
            return;
        }

        for (Map.Entry<Integer, Double> entry
                : vector.entrySet()) {

            entry.setValue(
                    entry.getValue()
                            / norm
            );
        }
    }

}