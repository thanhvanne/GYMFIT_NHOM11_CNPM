package com.gymfit.chat.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.nlu.TextNormalizer;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Sinh tập dữ liệu huấn luyện từ {@code chatbot/grammar.json}.
 *
 * <p>Cách chạy:
 * <pre>mvn -q exec:java@train</pre>
 *
 * <p>Chống rò rỉ: mỗi mẫu mang một {@code group}; tách tập theo
 * {@code abs(hash(group)) % 100}. Ngoài ra mỗi câu <b>chỉ thuộc một group duy nhất</b>
 * (dedupe toàn cục sau khi normalize) nên không thể xuất hiện ở cả train và test.
 */
@Slf4j
public final class DatasetGenerator {

    public static final String GRAMMAR =
            "chatbot/grammar.json";

    public static final String OUTPUT_DIR =
            "src/main/resources/chatbot/dataset";

    /** Log gán nhãn từ hệ thống (T12) — nạp thêm vào tập train. */
    public static final String SEED_FROM_LOGS =
            "src/main/resources/chatbot/seed_from_logs.jsonl";

    public static final int MAX_PER_TEMPLATE =
            220;

    /**
     * OUT_OF_SCOPE không được chiếm quá 12% tập train
     * → giới hạn số mẫu sinh từ template cho intent này.
     */
    public static final int OUT_OF_SCOPE_PER_TEMPLATE =
            80;

    /**
     * Mỗi template được chia nhỏ thành nhiều group để tỉ lệ
     * train/val/test (70/15/15) được cân bằng theo từng intent.
     */
    public static final int GROUP_SIZE =
            25;

    public static final long RANDOM_SEED =
            42L;

    /** Ngưỡng chia tập (%). */
    private static final int TRAIN_UNTIL = 70;
    private static final int VAL_UNTIL = 85;

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private DatasetGenerator() {
    }

    /**
     * Một mẫu huấn luyện.
     *
     * @param text   câu gõ thô (có dấu / teencode)
     * @param intent nhãn
     * @param group  nhóm để tách tập
     */
    public record Sample(
            String text,
            String intent,
            String group
    ) {
    }

    public static void main(
            String[] args
    ) throws Exception {
        Random random =
                new Random(RANDOM_SEED);

        List<Sample> samples =
                generate(random);

        Map<String, List<Sample>> splits =
                split(samples);

        Path directory =
                Paths.get(OUTPUT_DIR);

        Files.createDirectories(
                directory
        );

        write(
                directory.resolve("train.jsonl"),
                splits.get("train")
        );

        write(
                directory.resolve("val.jsonl"),
                splits.get("val")
        );

        write(
                directory.resolve("test.jsonl"),
                splits.get("test")
        );

        printStats(samples, splits);
    }

    // ------------------------------------------------------------------
    // Sinh mẫu
    // ------------------------------------------------------------------

    public static List<Sample> generate(
            Random random
    ) throws Exception {

        Map<String, List<Sample>> pool =
                new LinkedHashMap<>();

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        // normalizer dùng để dedupe; bản đầy đủ (kể cả teencode) nạp ở constructor.
        Map<String, Sample> seen =
                new LinkedHashMap<>();

        JsonNode grammar =
                readGrammar();

        JsonNode slots =
                grammar.path("slots");

        JsonNode intents =
                grammar.path("intents");

        intents.fields()
                .forEachRemaining(entry -> {

                    String intent =
                            entry.getKey();

                    List<Sample> list =
                            pool.computeIfAbsent(
                                    intent,
                                    key -> new ArrayList<>()
                            );

                    JsonNode seeds =
                            entry.getValue()
                                    .path("seeds");

                    int seedIndex =
                            0;

                    for (JsonNode seed : seeds) {

                        add(
                                list,
                                seen,
                                normalizer,
                                seed.asText(),
                                intent,
                                intent + "#s" + seedIndex++
                        );
                    }

                    JsonNode templates =
                            entry.getValue()
                                    .path("templates");

                    int templateIndex =
                            0;

                    for (JsonNode template : templates) {

                        int cap =
                                "OUT_OF_SCOPE".equals(
                                        intent
                                )
                                        ? OUT_OF_SCOPE_PER_TEMPLATE
                                        : MAX_PER_TEMPLATE;

                        String pattern =
                                template.asText();

                        // Template chỉ có 1–2 slot thì không gian tổ hợp quá nhỏ
                        // → tự thêm slot lịch sự để đủ độ phủ.
                        if (combinationSpace(
                                pattern,
                                slots
                        ) < cap) {
                            pattern =
                                    pattern + " {extra}";
                        }

                        List<String> rendered =
                                expand(
                                        pattern,
                                        slots,
                                        random,
                                        cap
                                );

                        for (int i = 0; i < rendered.size(); i++) {

                            add(
                                    list,
                                    seen,
                                    normalizer,
                                    rendered.get(i),
                                    intent,
                                    intent
                                            + "#t"
                                            + templateIndex
                                            + "_p"
                                            + (i / GROUP_SIZE)
                            );
                        }

                        templateIndex++;
                    }
                });

        loadSeedFromLogs(
                pool,
                seen,
                normalizer
        );

        // Augmentation chạy sau khi dedupe để không sinh trùng.
        List<Sample> result =
                new ArrayList<>();

        for (List<Sample> list : pool.values()) {

            List<Sample> copy =
                    new ArrayList<>(list);

            result.addAll(copy);

            for (Sample sample : copy) {

                for (String variant :
                        Augmenter.variants(
                                sample.text(),
                                random
                        )) {

                    add(
                            result,
                            seen,
                            normalizer,
                            variant,
                            sample.intent(),
                            sample.group()
                    );
                }
            }
        }

        return result;
    }

    private static void add(
            List<Sample> target,
            Map<String, Sample> seen,
            TextNormalizer normalizer,
            String rawText,
            String intent,
            String group
    ) {
        String text =
                rawText == null
                        ? ""
                        : rawText.trim();

        if (text.isEmpty()) {
            return;
        }

        String key =
                normalizer.normalize(text)
                        .plain();

        if (key.isBlank()) {
            return;
        }

        // Dedupe toàn cục: một câu chỉ thuộc group đầu tiên xuất hiện
        // -> không thể rò rỉ giữa train và test.
        if (seen.containsKey(key)) {
            return;
        }

        Sample sample =
                new Sample(
                        text,
                        intent,
                        group
                );

        seen.put(
                key,
                sample
        );

        target.add(sample);
    }

    /**
     * Nạp thêm mẫu đã gán nhãn từ hệ thống (nhóm {@code CAND#id}).
     */
    private static void loadSeedFromLogs(
            Map<String, List<Sample>> pool,
            Map<String, Sample> seen,
            TextNormalizer normalizer
    ) throws Exception {

        Path path =
                Paths.get(SEED_FROM_LOGS);

        if (!Files.exists(path)) {
            return;
        }

        List<String> lines =
                Files.readAllLines(
                        path,
                        StandardCharsets.UTF_8
                );

        for (String line : lines) {

            if (line.isBlank()) {
                continue;
            }

            JsonNode node =
                    MAPPER.readTree(line);

            String text =
                    node.path("text")
                            .asText("");

            String intent =
                    node.path("intent")
                            .asText("");

            if (text.isBlank()
                    || intent.isBlank()) {
                continue;
            }

            String group =
                    node.path("group")
                            .asText(
                                    "CAND#"
                                            + text.hashCode()
                            );

            add(
                    pool.computeIfAbsent(
                            intent,
                            key -> new ArrayList<>()
                    ),
                    seen,
                    normalizer,
                    text,
                    intent,
                    group
            );
        }

        log.info(
                "Đã nạp {} mẫu từ seed_from_logs.jsonl",
                lines.size()
        );
    }

    // ------------------------------------------------------------------
    // Sinh tổ hợp slot
    // ------------------------------------------------------------------

    /**
     * Thay {@code {slot}} bằng ngẫu nhiên một giá trị trong slot đó.
     * Giới hạn {@code limit} mẫu (lấy ngẫu nhiên, tránh trùng).
     */
    static List<String> expand(
            String template,
            JsonNode slots,
            Random random,
            int limit
    ) {
        List<String> result =
                new ArrayList<>();

        Set<String> produced =
                new LinkedHashSet<>();

        int guard =
                0;

        while (produced.size() < limit
                && guard < limit * 20) {

            guard++;

            String rendered =
                    template;

            for (String name : slotNames(template)) {

                JsonNode options =
                        slots.path(name);

                String value;

                if (!options.isArray()
                        || options.size() == 0) {

                    // Slot chưa khai báo -> để trống cho an toàn.
                    value =
                            "";
                } else {
                    value =
                            options.get(
                                    random.nextInt(
                                            options.size()
                                    )
                            )
                                    .asText("");
                }

                rendered =
                        rendered.replace(
                                "{" + name + "}",
                                value
                        );
            }

            rendered =
                    normalizeSpaces(
                            rendered
                    );

            if (!rendered.isBlank()
                    && produced.add(
                    rendered
            )) {
                result.add(
                        rendered
                );
            }
        }

        return result;
    }

    private static final java.util.regex.Pattern SLOT =
            java.util.regex.Pattern.compile(
                    "\\{(\\w+)\\}"
            );

    /**
     * Số tổ hợp tối đa của một template (giới hạn tràn để tránh tính quá lớn).
     */
    static long combinationSpace(
            String template,
            JsonNode slots
    ) {
        long space =
                1;

        for (String name : slotNames(template)) {

            JsonNode options =
                    slots.path(name);

            int size =
                    options.isArray()
                            ? options.size()
                            : 1;

            space *=
                    Math.max(
                            1,
                            size
                    );

            if (space > 1_000_000) {
                return space;
            }
        }

        return space;
    }

    private static List<String> slotNames(
            String template
    ) {
        List<String> names =
                new ArrayList<>();

        var matcher =
                SLOT.matcher(template);

        while (matcher.find()) {
            names.add(
                    matcher.group(1)
            );
        }

        return names;
    }

    private static String normalizeSpaces(
            String text
    ) {
        return text.trim()
                .replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------
    // Tách tập
    // ------------------------------------------------------------------

    /**
     * Chia theo group để biến thể augment không bị rò rỉ.
     */
    public static Map<String, List<Sample>> split(
            List<Sample> samples
    ) {
        Map<String, List<Sample>> splits =
                new LinkedHashMap<>();

        splits.put(
                "train",
                new ArrayList<>()
        );

        splits.put(
                "val",
                new ArrayList<>()
        );

        splits.put(
                "test",
                new ArrayList<>()
        );

        for (Sample sample : samples) {

            int bucket =
                    bucket(
                            sample.group()
                    );

            if (bucket < TRAIN_UNTIL) {
                splits.get("train")
                        .add(sample);
            } else if (bucket < VAL_UNTIL) {
                splits.get("val")
                        .add(sample);
            } else {
                splits.get("test")
                        .add(sample);
            }
        }

        return splits;
    }

    /**
     * Bucket 0–99 của group.
     *
     * <p>Không dùng thẳng {@code String.hashCode() % 100}: các group cùng dạng
     * ({@code X#t0_p1}, {@code X#t1_p1}…) cho hash rất tương quan nên phân bố lệch
     * (thực tế 55/24/21 thay vì 70/15/15). Trộn kiểu murmur finalizer trước khi chia.
     */
    static int bucket(
            String group
    ) {
        int h =
                group.hashCode();

        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);

        return Math.floorMod(
                h,
                100
        );
    }

    // ------------------------------------------------------------------
    // Ghi file & thống kê
    // ------------------------------------------------------------------

    private static void write(
            Path path,
            List<Sample> samples
    ) throws Exception {
        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             path,
                             StandardCharsets.UTF_8
                     )) {

            for (Sample sample : samples) {

                writer.write(
                        MAPPER.writeValueAsString(
                                new java.util.LinkedHashMap<>(
                                        java.util.Map.of(
                                                "text",
                                                sample.text(),
                                                // áp alias: 6 FAQ cũ → FAQ_GENERAL
                                                "intent",
                                                IntentAliases.map(
                                                        sample.intent()
                                                ),
                                                "group",
                                                sample.group()
                                        )
                                )
                        )
                );

                writer.newLine();
            }
        }

        log.info(
                "Đã ghi {} mẫu → {}",
                samples.size(),
                path
        );
    }

    private static void printStats(
            List<Sample> samples,
            Map<String, List<Sample>> splits
    ) {
        System.out.println(
                "=== THỐNG KÊ DATASET ==="
        );

        System.out.printf(
                Locale.ROOT,
                "Tổng: %d | train %d | val %d | test %d%n",
                samples.size(),
                splits.get("train")
                        .size(),
                splits.get("val")
                        .size(),
                splits.get("test")
                        .size()
        );

        Map<String, int[]> perIntent =
                new LinkedHashMap<>();

        for (Sample sample : splits.get("train")) {
            perIntent.computeIfAbsent(
                    sample.intent(),
                    key -> new int[1]
            )[0]++;
        }

        System.out.println(
                "--- số mẫu train / intent ---"
        );

        perIntent.entrySet()
                .stream()
                .sorted(
                        Map.Entry.comparingByKey()
                )
                .forEach(entry ->
                        System.out.printf(
                                Locale.ROOT,
                                "%-24s %d%n",
                                entry.getKey(),
                                entry.getValue()[0]
                        )
                );

        int outOfScope =
                perIntent.getOrDefault(
                        "OUT_OF_SCOPE",
                        new int[1]
                )[0];

        int trainTotal =
                splits.get("train")
                        .size();

        System.out.printf(
                Locale.ROOT,
                "OUT_OF_SCOPE train: %d (%.2f%%)%n",
                outOfScope,
                trainTotal == 0
                        ? 0
                        : 100.0 * outOfScope / trainTotal
        );
    }

    private static JsonNode readGrammar()
            throws Exception {
        try (InputStream input =
                     DatasetGenerator.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     GRAMMAR
                             )) {

            if (input == null) {
                throw new IllegalStateException(
                        "Không tìm thấy " + GRAMMAR
                );
            }

            return MAPPER.readTree(input);
        }
    }

}