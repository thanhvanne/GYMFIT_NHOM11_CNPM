package com.gymfit.chat.training;

import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlu.TextNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tra tiêu chí nghiệm thu của tập dữ liệu sinh tự động (T4).
 */
class DatasetGeneratorTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static Map<String, List<String>> dataset;

    private static TextNormalizer normalizer;

    @BeforeAll
    static void load() throws Exception {
        normalizer =
                new TextNormalizer();

        normalizer.load();

        dataset =
                new LinkedHashMap<>();

        for (String split : List.of(
                "train",
                "val",
                "test"
        )) {

            Path path =
                    Paths.get(
                            DatasetGenerator.OUTPUT_DIR
                    ).resolve(split + ".jsonl");

            assertTrue(
                    Files.exists(path),
                    "Thiếu " + path
                            + " — hãy chạy DatasetGenerator"
            );

            List<String> texts =
                    new ArrayList<>();

            for (String line : Files.readAllLines(
                    path,
                    StandardCharsets.UTF_8
            )) {

                if (line.isBlank()) {
                    continue;
                }

                JsonNode node =
                        MAPPER.readTree(line);

                assertTrue(
                        node.hasNonNull("text"),
                        "Dòng thiếu field text"
                );

                assertTrue(
                        node.hasNonNull("intent"),
                        "Dòng thiếu field intent"
                );

                assertTrue(
                        node.hasNonNull("group"),
                        "Dòng thiếu field group"
                );

                texts.add(
                        node.path("text")
                                .asText()
                );
            }

            dataset.put(
                    split,
                    texts
            );
        }
    }

    @Test
    @DisplayName("Tổng số mẫu ≥ 10.000")
    void totalSize() {
        int total =
                dataset.get("train")
                        .size()
                        + dataset.get("val")
                                .size()
                        + dataset.get("test")
                                .size();

        assertTrue(
                total >= 10_000,
                "Tổng mẫu = " + total
                        + " (< 10.000)"
        );
    }

    @Test
    @DisplayName("Không câu nào (sau normalize) xuất hiện ở cả train và test")
    void noLeakageTrainTest() {

        Set<String> train =
                normalized(
                        dataset.get("train")
                );

        Set<String> test =
                normalized(
                        dataset.get("test")
                );

        Set<String> overlap =
                new HashSet<>(train);

        overlap.retainAll(test);

        assertTrue(
                overlap.isEmpty(),
                "Rò rỉ " + overlap.size()
                        + " câu, ví dụ: "
                        + overlap.stream()
                        .limit(5)
                        .toList()
        );
    }

    @Test
    @DisplayName("Không rò rỉ train ↔ val")
    void noLeakageTrainVal() {

        Set<String> train =
                normalized(
                        dataset.get("train")
                );

        Set<String> val =
                normalized(
                        dataset.get("val")
                );

        Set<String> overlap =
                new HashSet<>(train);

        overlap.retainAll(val);

        assertTrue(
                overlap.isEmpty(),
                "Rò rỉ " + overlap.size()
                        + " câu giữa train và val"
        );
    }

    @Test
    @DisplayName("OUT_OF_SCOPE ≤ 12% tập train")
    void outOfScopeRatio() throws Exception {

        int outOfScope =
                countIntent(
                        "train",
                        Intent.OUT_OF_SCOPE
                );

        int total =
                dataset.get("train")
                        .size();

        double ratio =
                (double) outOfScope / total;

        assertTrue(
                ratio <= 0.12,
                "OUT_OF_SCOPE = "
                        + outOfScope + "/"
                        + total
                        + " = "
                        + ratio
        );
    }

    @Test
    @DisplayName("Mỗi intent có ≥ 150 mẫu train")
    void everyIntentHasEnoughSamples()
            throws Exception {

        Map<String, Integer> counts =
                countPerIntent("train");

        List<String> missing =
                new ArrayList<>();

        for (Intent intent : Intent.values()) {

            int count =
                    counts.getOrDefault(
                            intent.name(),
                            0
                    );

            if (count < 150) {
                missing.add(
                        intent.name() + "="
                                + count
                );
            }
        }

        assertTrue(
                missing.isEmpty(),
                "Intent thiếu mẫu: " + missing
        );
    }

    @Test
    @DisplayName("Mọi nhãn đều là intent hợp lệ")
    void labelsAreValidIntents()
            throws Exception {

        for (String split : dataset.keySet()) {

            for (String intent : countPerIntent(
                    split
            ).keySet()) {

                assertTrue(
                        Intent.valueOf(intent)
                                != null,
                        "Nhãn không hợp lệ: "
                                + intent
                );
            }
        }
    }

    @Test
    @DisplayName("Tất cả 36 intent đều có mặt trong tập train")
    void allIntentsPresent()
            throws Exception {

        Map<String, Integer> counts =
                countPerIntent("train");

        assertEquals(
                Intent.values().length,
                counts.size(),
                "Thiếu intent trong tập train"
        );
    }

    @Test
    @DisplayName("Bucket chia tập phân bố 70/15/15")
    void splitRatio() {

        int train =
                dataset.get("train")
                        .size();

        int val =
                dataset.get("val")
                        .size();

        int test =
                dataset.get("test")
                                .size();

        int total =
                train + val + test;

        double trainRatio =
                (double) train / total;

        assertTrue(
                trainRatio > 0.60
                        && trainRatio < 0.80,
                "Tỉ lệ train = " + trainRatio
        );

        assertTrue(
                val > 0 && test > 0,
                "val/test phải khác 0"
        );
    }

    @Test
    @DisplayName("Cùng group không bị tách ra hai tập")
    void groupNotSplitAcrossFiles()
            throws Exception {

        Map<String, Set<String>> groupSplits =
                new HashMap<>();

        for (String split : dataset.keySet()) {

            Path path =
                    Paths.get(
                            DatasetGenerator.OUTPUT_DIR
                    ).resolve(split + ".jsonl");

            for (String line : Files.readAllLines(
                    path,
                    StandardCharsets.UTF_8
            )) {

                if (line.isBlank()) {
                    continue;
                }

                String group =
                        MAPPER.readTree(line)
                                .path("group")
                                .asText();

                groupSplits
                        .computeIfAbsent(
                                group,
                                key -> new HashSet<>()
                        )
                        .add(split);
            }
        }

        List<String> broken =
                groupSplits.entrySet()
                        .stream()
                        .filter(entry ->
                                entry.getValue()
                                        .size() > 1)
                        .map(Map.Entry::getKey)
                        .limit(5)
                        .toList();

        assertTrue(
                broken.isEmpty(),
                "Group bị rò rỉ sang nhiều tập: "
                        + broken
        );
    }

    // ------------------------------------------------------------------

    private static Set<String> normalized(
            List<String> texts
    ) {
        Set<String> result =
                new HashSet<>();

        for (String text : texts) {
            result.add(
                    normalizer.normalize(text)
                            .plain()
            );
        }

        return result;
    }

    private static int countIntent(
            String split,
            Intent intent
    ) throws Exception {

        Path path =
                Paths.get(
                        DatasetGenerator.OUTPUT_DIR
                ).resolve(split + ".jsonl");

        int count =
                0;

        for (String line : Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        )) {

            if (line.isBlank()) {
                continue;
            }

            if (intent.name().equals(
                    MAPPER.readTree(line)
                            .path("intent")
                            .asText()
            )) {
                count++;
            }
        }

        return count;
    }

    private static Map<String, Integer> countPerIntent(
            String split
    ) throws Exception {

        Path path =
                Paths.get(
                        DatasetGenerator.OUTPUT_DIR
                ).resolve(split + ".jsonl");

        Map<String, Integer> counts =
                new HashMap<>();

        for (String line : Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        )) {

            if (line.isBlank()) {
                continue;
            }

            counts.merge(
                    MAPPER.readTree(line)
                            .path("intent")
                            .asText(),
                    1,
                    Integer::sum
            );
        }

        return counts;
    }

}