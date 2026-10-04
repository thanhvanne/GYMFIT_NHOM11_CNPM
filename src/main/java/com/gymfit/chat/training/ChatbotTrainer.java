package com.gymfit.chat.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.nlu.ChatPipeline;
import com.gymfit.chat.nlu.DateTimeParser;
import com.gymfit.chat.nlu.FeatureExtractor;
import com.gymfit.chat.nlu.IntentModel;
import com.gymfit.chat.nlu.SparseVector;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.chat.nlu.entity.EntityExtractor;
import com.gymfit.chat.nlu.entity.EntityType;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import com.gymfit.chat.nlu.entity.Span;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Huấn luyện mô hình phân loại intent (softmax regression) cho chatbot.
 *
 * <p>Chạy:
 * <pre>mvn -q exec:java@train</pre>
 *
 * <p>Pipeline <b>giống hệt</b> lúc dự đoán:
 * <pre>
 * raw → TextNormalizer.plain → EntityExtractor → MASK → features → IntentModel.vectorize
 * </pre>
 * Hàm {@link #mask(String, TextNormalizer, EntityExtractor)} và
 * {@link IntentModel#vectorize(String)} dùng chung cho cả train lẫn predict —
 * tránh lỗi "điểm test cao nhưng chạy thật sai".
 */
@Slf4j
public final class ChatbotTrainer {

    /** Ngày cố định khi huấn luyện để tách ngày/giờ khỏi tín hiệu thời gian. */
    public static final String TRAINING_DAY =
            "2026-10-03";

    public static final String DATASET_DIR =
            "src/main/resources/chatbot/dataset";

    /**
     * Holdout V2 (schema {@code text,intent,tier,tags,role}) – dùng khi có;
     * nếu thiếu thì rơi về {@link #HOLDOUT_LEGACY}.
     */
    public static final String HOLDOUT =
            "src/main/resources/chatbot/holdout_v2.jsonl";

    /** Holdout V1 (chỉ {@code text,intent,group}) – bản dự phòng. */
    public static final String HOLDOUT_LEGACY =
            "src/main/resources/chatbot/holdout.jsonl";

    public static final String MODEL_PATH =
            "src/main/resources/chatbot/model/intent-model.bin";

    public static final String REPORT_PATH =
            "docs/chatbot/training-report.md";

    /** Ma trận nhầm lẫn trên holdout – mục 7.2 của plan V2. */
    public static final String CONFUSION_PATH =
            "docs/chatbot/confusion.csv";

    // ---- Siêu tham số ----
    public static final int EPOCHS = 40;
    public static final double LR0 = 0.3;
    public static final double DECAY = 0.05;
    public static final double L2 = 1e-5;

    public static final long SEED = 42L;

    /** Số mẫu tối đa cho mỗi lần grid search (grid chỉ dùng để chọn tham số). */
    public static final int GRID_SAMPLE = 20_000;

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private ChatbotTrainer() {
    }

    /**
     * Một mẫu trong dataset.
     *
     * @param text câu gõ thô (có dấu / teencode)
     */
    public record RawSample(
            String text,
            String intent,
            String group
    ) {
    }

    public static void main(
            String[] args
    ) throws Exception {

        long started =
                System.currentTimeMillis();

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        EntityExtractor extractor =
                staticExtractor();

        List<RawSample> trainRaw =
                readJsonl(
                        Paths.get(
                                DATASET_DIR
                        ).resolve("train.jsonl")
                );

        List<RawSample> valRaw =
                readJsonl(
                        Paths.get(
                                DATASET_DIR
                        ).resolve("val.jsonl")
                );

        List<RawSample> testRaw =
                readJsonl(
                        Paths.get(
                                DATASET_DIR
                        ).resolve("test.jsonl")
                );

        log.info(
                "train={} val={} test={}",
                trainRaw.size(),
                valRaw.size(),
                testRaw.size()
        );

        String[] labels =
                labels(trainRaw, valRaw, testRaw);

        Map<String, Integer> labelIndex =
                new LinkedHashMap<>();

        for (int i = 0; i < labels.length; i++) {
            labelIndex.put(
                    labels[i],
                    i
            );
        }

        Map<String, Double> idf =
                FeatureExtractor.computeIdf(
                        features(
                                trainRaw,
                                normalizer,
                                extractor
                        )
                );

        log.info(
                "Số feature: {}",
                idf.size()
        );

        IntentModel holder =
                new IntentModel(
                        labels,
                        idf,
                        idf.size()
                );

        SparseVector[] trainX =
                vectorize(
                        features(
                                trainRaw,
                                normalizer,
                                extractor
                        ),
                        holder
                );

        int[] trainY =
                labels(
                        trainRaw,
                        labelIndex
                );

        SparseVector[] valX =
                vectorize(
                        features(
                                valRaw,
                                normalizer,
                                extractor
                        ),
                        holder
                );

        int[] valY =
                labels(
                        valRaw,
                        labelIndex
                );

        SparseVector[] testX =
                vectorize(
                        features(
                                testRaw,
                                normalizer,
                                extractor
                        ),
                        holder
                );

        int[] testY =
                labels(
                        testRaw,
                        labelIndex
                );

        // ---- Chọn siêu tham số trên val ----
        double[] best =
                gridSearch(
                        labels,
                        idf,
                        trainX,
                        trainY,
                        valX,
                        valY
                );

        double bestLr0 =
                best[0];

        double bestL2 =
                best[1];

        log.info(
                "Siêu tham số tốt nhất: lr0={} l2={} macroF1={}",
                bestLr0,
                bestL2,
                best[2]
        );

        // ---- Huấn luyện model cuối trên TRAIN ----
        // Giữ val là tập đánh giá độc lập (không đưa vào train) để
        // TEST và HOLDOUT thực sự là mẫu đánh giá chưa từng thấy.
        IntentModel model =
                train(
                        labels,
                        idf,
                        trainX,
                        trainY,
                        bestLr0,
                        bestL2
                );

        Evaluator.Result valResult =
                evaluate(
                        model,
                        valX,
                        valY,
                        labels
                );

        Evaluator.Result testResult =
                evaluate(
                        model,
                        testX,
                        testY,
                        labels
                );

        HoldoutEvaluation holdout =
                evaluateHoldout(
                        model,
                        normalizer,
                        extractor,
                        labelIndex,
                        labels
                );

        Path modelPath =
                Paths.get(MODEL_PATH);

        Files.createDirectories(
                modelPath.getParent()
        );

        try (OutputStream out =
                     Files.newOutputStream(
                             modelPath
                     )) {
            model.save(out);
        }

        log.info(
                "Đã lưu model → {}",
                modelPath
        );

        if (holdout != null) {

            Path confusion =
                    Paths.get(CONFUSION_PATH);

            Files.createDirectories(
                    confusion.getParent()
            );

            Files.writeString(
                    confusion,
                    Evaluator.confusionCsv(
                            holdout.predictions()
                    ),
                    StandardCharsets.UTF_8
            );

            log.info(
                    "Đã ghi ma trận nhầm lẫn → {}",
                    confusion
            );
        }

        writeReport(
                trainRaw.size(),
                valRaw.size(),
                testRaw.size(),
                labels.length,
                idf.size(),
                bestLr0,
                bestL2,
                valResult,
                testResult,
                holdout
        );

        System.out.printf(
                Locale.ROOT,
                "Hoàn tất sau %d ms%n",
                System.currentTimeMillis() - started
        );
    }

    // ------------------------------------------------------------------
    // Huấn luyện
    // ------------------------------------------------------------------

    private static double[] gridSearch(
            String[] labels,
            Map<String, Double> idf,
            SparseVector[] x,
            int[] y,
            SparseVector[] vx,
            int[] vy
    ) {

        double[] lr0s = {
                0.1, 0.3, 0.5
        };

        double[] l2s = {
                1e-6, 1e-5, 1e-4
        };

        double bestF1 =
                -1;

        double bestLr0 =
                LR0;

        double bestL2 =
                L2;

        // Grid chỉ để chọn tham số → lấy một tập con để giảm thời gian.
        int step =
                Math.max(
                        1,
                        x.length / GRID_SAMPLE
                );

        int cap =
                (x.length + step - 1) / step;

        SparseVector[] gridX =
                new SparseVector[cap];

        int[] gridY =
                new int[cap];

        int count =
                0;

        for (int i = 0; i < x.length
                && count < cap; i += step) {

            gridX[count] =
                    x[i];

            gridY[count] =
                    y[i];

            count++;
        }

        log.info(
                "Grid search trên {} mẫu (tổng {})",
                count,
                x.length
        );

        List<double[]> combos =
                new ArrayList<>();

        for (double lr0 : lr0s) {
            for (double l2 : l2s) {
                combos.add(
                        new double[]{
                                lr0,
                                l2
                        }
                );
            }
        }

        ExecutorService pool =
                Executors.newFixedThreadPool(
                        Math.min(
                                combos.size(),
                                Math.max(
                                        4,
                                        Runtime.getRuntime()
                                                .availableProcessors()
                                                / 2
                                )
                        )
                );

        List<Future<double[]>> futures =
                new ArrayList<>();

        for (double[] combo : combos) {

            final double lr0 =
                    combo[0];

            final double l2 =
                    combo[1];

            futures.add(
                    pool.submit(
                            () -> {

                                IntentModel m =
                                        train(
                                                labels,
                                                idf,
                                                gridX,
                                                gridY,
                                                lr0,
                                                l2
                                        );

                                Evaluator.Result r =
                                        evaluate(
                                                m,
                                                vx,
                                                vy,
                                                labels
                                        );

                                return new double[]{
                                        lr0,
                                        l2,
                                        r.macroF1(),
                                        r.accuracy()
                                };
                            }
                    )
                );
        }

        pool.shutdown();

        for (Future<double[]> future : futures) {

            try {

                double[] row =
                        future.get();

                log.info(
                        "grid lr0={} l2={} -> macroF1={} acc={}",
                        row[0],
                        row[1],
                        String.format(
                                Locale.ROOT,
                                "%.4f",
                                row[2]
                        ),
                        String.format(
                                Locale.ROOT,
                                "%.4f",
                                row[3]
                        )
                );

                if (row[2] > bestF1) {
                    bestF1 =
                            row[2];

                    bestLr0 =
                            row[0];

                    bestL2 =
                            row[1];
                }

            } catch (Exception exception) {

                throw new IllegalStateException(
                        "Grid search thất bại",
                        exception
                );
            }
        }

        return new double[]{
                bestLr0,
                bestL2,
                bestF1
        };
    }

    /**
     * Softmax regression SGD tối ưu hoá cross-entropy có trọng số lớp.
     */
    public static IntentModel train(
            String[] labels,
            Map<String, Double> idf,
            SparseVector[] x,
            int[] y,
            double lr0,
            double l2
    ) {
        int k =
                labels.length;

        IntentModel model =
                new IntentModel(
                        labels,
                        idf,
                        idf.size()
                );

        int[] counts =
                new int[k];

        int maxCount =
                0;

        for (int label : y) {
            counts[label]++;

            maxCount =
                    Math.max(
                            maxCount,
                            counts[label]
                    );
        }

        // classWeight[c] = min(3, sqrt(maxCount / count[c]))
        double[] classWeight =
                new double[k];

        for (int i = 0; i < k; i++) {

            double weight =
                    counts[i] == 0
                            ? 1
                            : Math.sqrt(
                            (double) maxCount / counts[i]
                    );

            classWeight[i] =
                    Math.min(
                            3.0,
                            Math.max(
                                    1.0,
                                    weight
                            )
                    );
        }

        Random rnd =
                new Random(SEED);

        int[] order =
                new int[x.length];

        for (int i = 0; i < order.length; i++) {
            order[i] =
                    i;
        }

        double[] probs =
                new double[k];

        for (int epoch = 1; epoch <= EPOCHS; epoch++) {

            for (int i = order.length - 1; i > 0; i--) {

                int j =
                        rnd.nextInt(
                                i + 1
                        );

                int tmp =
                        order[i];

                order[i] =
                        order[j];

                order[j] =
                        tmp;
            }

            double lr =
                    lr0 / (1 + DECAY * epoch);

            for (int idx : order) {

                SparseVector vector =
                        x[idx];

                int[] fidx =
                        vector.indices();

                float[] fval =
                        vector.values();

                int n =
                        fidx.length;

                for (int c = 0; c < k; c++) {

                    float[] row =
                            model.W[c];

                    float s =
                            model.b[c];

                    for (int j = 0; j < n; j++) {
                        s += row[fidx[j]]
                                * fval[j];
                    }

                    probs[c] =
                            s;
                }

                IntentModel.softmax(
                        probs,
                        probs
                );

                int actual =
                        y[idx];

                double weight =
                        classWeight[actual];

                for (int c = 0; c < k; c++) {

                    double g =
                            (probs[c]
                                    - (c == actual
                                    ? 1
                                    : 0))
                                    * weight;

                    model.b[c] -=
                            lr * g;

                    float[] row =
                            model.W[c];

                    for (int j = 0; j < n; j++) {

                        int fi =
                                fidx[j];

                        float v =
                                fval[j];

                        row[fi] -= lr
                                * (g * v
                                + l2 * row[fi]);
                    }
                }
            }
        }

        return model;
    }

    // ------------------------------------------------------------------
    // Đánh giá
    // ------------------------------------------------------------------

    private static Evaluator.Result evaluate(
            IntentModel model,
            SparseVector[] x,
            int[] y,
            String[] labels
    ) {
        List<Evaluator.Prediction> predictions =
                new ArrayList<>();

        for (int i = 0; i < x.length; i++) {

            double[] probs =
                    model.probabilities(
                            x[i]
                    );

            int best =
                    argmax(probs);

            predictions.add(
                    new Evaluator.Prediction(
                            labels[best],
                            labels[y[i]],
                            probs[best]
                    )
            );
        }

        return Evaluator.evaluate(
                predictions
        );
    }

    /**
     * Kết quả trên holdout kèm danh sách dự đoán đầy đủ
     * (dùng để in câu sai và ghi {@code confusion.csv}).
     *
     * @param result      kết quả đánh giá
     * @param predictions từng dự đoán có {@code text/tier/tags}
     */
    private record HoldoutEvaluation(
            Evaluator.Result result,
            List<Evaluator.Prediction> predictions
    ) {
    }

    /** Ưu tiên holdout V2; thiếu thì dùng bản V1. */
    private static Path holdoutPath() {

        Path v2 =
                Paths.get(HOLDOUT);

        return Files.exists(v2)
                ? v2
                : Paths.get(HOLDOUT_LEGACY);
    }

    private static HoldoutEvaluation evaluateHoldout(
            IntentModel model,
            TextNormalizer normalizer,
            EntityExtractor extractor,
            Map<String, Integer> labelIndex,
            String[] labels
    ) throws Exception {

        Path path =
                holdoutPath();

        if (!Files.exists(path)) {
            return null;
        }

        List<JsonNode> holdout =
                readJsonlNodes(path);

        if (holdout.isEmpty()) {
            return null;
        }

        List<Evaluator.Prediction> predictions =
                new ArrayList<>();

        for (JsonNode node : holdout) {

            String intent =
                    IntentAliases.map(
                            node.path("intent")
                                    .asText("")
                    );

            Integer index =
                    labelIndex.get(intent);

            if (index == null) {
                continue;
            }

            String text =
                    node.path("text")
                            .asText("");

            String masked =
                    mask(
                            text,
                            normalizer,
                            extractor
                    );

            double[] probs =
                    model.probabilities(
                            masked
                    );

            int best =
                    argmax(probs);

            predictions.add(
                    new Evaluator.Prediction(
                            text,
                            labels[best],
                            intent,
                            probs[best],
                            node.path("tier")
                                    .asText(null),
                            readTags(node)
                    )
            );
        }

        if (predictions.isEmpty()) {
            return null;
        }

        return new HoldoutEvaluation(
                Evaluator.evaluate(
                        predictions
                ),
                predictions
        );
    }

    private static List<String> readTags(
            JsonNode node
    ) {

        List<String> tags =
                new ArrayList<>();

        JsonNode array =
                node.path("tags");

        if (array.isArray()) {
            array.forEach(tag ->
                    tags.add(
                            tag.asText()
                    )
            );
        }

        return tags;
    }

    private static List<JsonNode> readJsonlNodes(
            Path path
    ) throws Exception {

        List<JsonNode> result =
                new ArrayList<>();

        for (String line : Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        )) {

            if (line.isBlank()) {
                continue;
            }

            result.add(
                    MAPPER.readTree(line)
            );
        }

        return result;
    }

    private static int argmax(
            double[] values
    ) {
        int best =
                0;

        for (int i = 1; i < values.length; i++) {

            if (values[i] > values[best]) {
                best =
                        i;
            }
        }

        return best;
    }

    // ------------------------------------------------------------------
    // Chuẩn bị dữ liệu
    // ------------------------------------------------------------------

    /**
     * Chuẩn bị chuỗi cho mô hình: plain → mask span thời gian/tiền/mã.
     *
     * <p>Giữ nguyên SERVICE / BRANCH / TIER vì chúng mang tín hiệu; chỉ thay
     * DATE, TIME, MONEY, BOOKING_CODE bằng placeholder.
     */
    /**
     * Chuẩn bị chuỗi cho mô hình — dùng CHUNG {@link ChatPipeline} với lúc dự đoán.
     */
    public static String mask(
            String raw,
            TextNormalizer normalizer,
            EntityExtractor extractor
    ) {
        return PIPELINE.prepare(
                normalizer.normalize(raw),
                java.time.LocalDate.parse(
                        TRAINING_DAY
                )
        );
    }

    /** Pipeline dùng chung; khởi tạo tĩnh vì không phụ thuộc Spring. */
    private static final ChatPipeline PIPELINE =
            buildPipeline();

    private static ChatPipeline buildPipeline() {

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        EntityExtractor extractor =
                staticExtractor();

        return new ChatPipeline(
                normalizer,
                extractor
        );
    }
    /** Wrapper nhỏ để tránh gọi normalize hai lần. */
    private record NormalizedTextHolder(
            com.gymfit.chat.nlu.NormalizedText text
    ) {
    }

    public static IntentModel loadModel()
            throws Exception {

        try (InputStream in =
                     Files.newInputStream(
                             Paths.get(MODEL_PATH)
                     )) {
            return IntentModel.load(in);
        }
    }

    private static List<RawSample> readJsonl(
            Path path
    ) throws Exception {

        List<RawSample> result =
                new ArrayList<>();

        if (!Files.exists(path)) {
            return result;
        }

        for (String line : Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        )) {

            if (line.isBlank()) {
                continue;
            }

            JsonNode node =
                    MAPPER.readTree(line);

            result.add(
                    new RawSample(
                            node.path("text")
                                    .asText(""),
                            node.path("intent")
                                    .asText(""),
                            node.path("group")
                                    .asText("")
                    )
            );
        }

        return result;
    }

    private static List<List<String>> features(
            List<RawSample> samples,
            TextNormalizer normalizer,
            EntityExtractor extractor
    ) {
        List<List<String>> result =
                new ArrayList<>();

        for (RawSample sample : samples) {

            result.add(
                    FeatureExtractor.features(
                            mask(
                                    sample.text(),
                                    normalizer,
                                    extractor
                            )
                    )
            );
        }

        return result;
    }

    private static SparseVector[] vectorize(
            List<List<String>> docs,
            IntentModel holder
    ) {
        SparseVector[] result =
                new SparseVector[docs.size()];

        for (int i = 0; i < docs.size(); i++) {
            result[i] =
                    holder.vectorize(
                            docs.get(i)
                    );
        }

        return result;
    }

    private static int[] labels(
            List<RawSample> samples,
            Map<String, Integer> index
    ) {
        int[] result =
                new int[samples.size()];

        for (int i = 0; i < samples.size(); i++) {

            Integer value =
                    index.get(
                            samples.get(i)
                                    .intent()
                    );

            result[i] =
                    value == null
                            ? 0
                            : value;
        }

        return result;
    }

    private static String[] labels(
            List<RawSample> train,
            List<RawSample> val,
            List<RawSample> test
    ) {

        TreeMap<String, Boolean> all =
                new TreeMap<>();

        List<RawSample> allSamples =
                new ArrayList<>();

        allSamples.addAll(train);
        allSamples.addAll(val);
        allSamples.addAll(test);

        for (RawSample sample : allSamples) {
            all.put(
                    sample.intent(),
                    Boolean.TRUE
            );
        }

        return all.keySet()
                .toArray(
                        new String[0]
                );
    }

    /** Gazetteer tĩnh cho trainer (không cần DB). */
    private static EntityExtractor staticExtractor() {

        Map<String, Long> branches =
                new HashMap<>();

        branches.put("q1", 1L);
        branches.put("quan 1", 1L);
        branches.put("chi nhanh q1", 1L);
        branches.put("q7", 2L);
        branches.put("quan 7", 2L);
        branches.put("chi nhanh q7", 2L);
        branches.put("td", 3L);
        branches.put("thu duc", 3L);
        branches.put("chi nhanh thu duc", 3L);
        branches.put("bt", 4L);
        branches.put("binh thanh", 4L);
        branches.put(
                "chi nhanh binh thanh",
                4L
        );

        GazetteerProvider gazetteer =
                new GazetteerProvider() {

                    @Override
                    public Map<String, Long> branchAliases() {
                        return branches;
                    }

                    @Override
                    public Map<String, String> productAliases() {
                        return Map.of();
                    }
                };

        EntityExtractor extractor =
                new EntityExtractor(
                        gazetteer,
                        new DateTimeParser()
                );

        extractor.load();

        return extractor;
    }

    private static void writeReport(
            int trainSize,
            int valSize,
            int testSize,
            int labelCount,
            int featureCount,
            double lr0,
            double l2,
            Evaluator.Result val,
            Evaluator.Result test,
            HoldoutEvaluation holdout
    ) throws Exception {

        StringBuilder sb =
                new StringBuilder();

        sb.append("# Báo cáo huấn luyện chatbot GYMFIT\n\n");

        sb.append(
                String.format(
                        Locale.ROOT,
                        "- Số mẫu train: %d\n"
                                + "- Số mẫu val: %d\n"
                                + "- Số mẫu test: %d\n"
                                + "- Số intent: %d\n"
                                + "- Số feature: %d\n"
                                + "- Siêu tham số: lr0=%.1f, l2=%g, epochs=%d\n\n",
                        trainSize,
                        valSize,
                        testSize,
                        labelCount,
                        featureCount,
                        lr0,
                        l2,
                        EPOCHS
                )
        );

        if (val != null) {
            sb.append(Evaluator.toMarkdown(
                    "VAL (sinh tự động — dùng để chọn siêu tham số)",
                    val
            ));
        }

        if (test != null) {
            sb.append(Evaluator.toMarkdown(
                    "TEST (sinh tự động)",
                    test
            ));
        }

        if (holdout != null) {

            List<Evaluator.Prediction> predictions =
                    holdout.predictions();

            sb.append(Evaluator.toMarkdown(
                    "HOLDOUT (viết tay — KPI thật)",
                    holdout.result()
            ));

            sb.append(Evaluator.toMarkdown(
                    "HOLDOUT theo tier",
                    Evaluator.byTier(predictions)
            ));

            sb.append(Evaluator.toMarkdown(
                    "HOLDOUT theo tag",
                    Evaluator.byTag(predictions)
            ));

            List<String> wrong =
                    Evaluator.wrongPredictions(predictions);

            sb.append(
                    "### HOLDOUT — danh sách câu sai ("
                            + wrong.size()
                            + ")\n\n"
            );

            wrong.forEach(line ->
                    sb.append("- ")
                            .append(line)
                            .append('\n')
            );

            sb.append('\n');

        } else {
            sb.append(
                    "### HOLDOUT\n\nChưa có holdout.jsonl.\n\n"
            );
        }

        Path report =
                Paths.get(REPORT_PATH);

        Files.createDirectories(
                report.getParent()
        );

        Files.writeString(
                report,
                sb.toString(),
                StandardCharsets.UTF_8
        );

        log.info(
                "Đã ghi báo cáo → {}",
                report
        );
    }

}