package com.gymfit.chat.knowledge;

import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.user.RoleCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Truy vấn FAQ theo TF-IDF + cosine (V2-3, plan 5.3).
 *
 * <p><b>Chỉ mục:</b> mỗi {@code question} → {@link TextNormalizer#toPlain} →
 * vector TF-IDF (word 1–2-gram + char 3–5-gram) L2-chuẩn hóa; lưu {@code entryId}
 * cho từng vector.
 *
 * <p><b>Truy vấn:</b> {@code plain} của câu người dùng → vector (chỉ giữ feature
 * có trong chỉ mục) → cosine với mọi vector → điểm mục = <b>max</b> theo câu hỏi,
 * cộng {@value #TAG_BONUS} nếu mục trùng ≥ 1 {@code tag} với truy vấn.
 *
 * <p><b>Ngưỡng:</b> ≥ {@value #THRESHOLD_ANSWER} → trả lời; từ
 * {@value #THRESHOLD_SUGGEST} đến {@value #THRESHOLD_ANSWER} → chip "Có phải bạn
 * muốn hỏi:"; dưới {@value #THRESHOLD_SUGGEST} → miss.
 *
 * <p><b>Lọc:</b> chỉ mục có {@code roles} chứa vai trò hiện tại và
 * {@code status ≠ PENDING_FEATURE}.
 *
 * <p>Không dùng thư viện ngoài — mọi phép tính đều tự viết để giữ đúng ràng buộc
 * "không thêm dependency".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqRetriever {

    /** Điểm ≥ ngưỡng này → trả lời trực tiếp. */
    public static final double THRESHOLD_ANSWER =
            0.55;

    /** Điểm ≥ ngưỡng này (và < answer) → hiện chip gợi ý. */
    public static final double THRESHOLD_SUGGEST =
            0.35;

    /** Bonus khi mục trùng ≥ 1 tag với truy vấn. */
    public static final double TAG_BONUS =
            0.05;

    /** Số chip tối đa ở lượt gợi ý. */
    public static final int MAX_CHIPS =
            3;

    private static final int WORD_N_MIN =
            1;

    private static final int WORD_N_MAX =
            2;

    private static final int CHAR_N_MIN =
            3;

    private static final int CHAR_N_MAX =
            5;

    private final FaqKnowledgeBase knowledgeBase;

    /** Một vector đã chuẩn hóa, gắn với mục FAQ. */
    private record Vector(
            String entryId,
            Map<String, Double> weights
    ) {
    }

    /** Một dòng trong bảng xếp hạng. */
    private record Ranked(
            String entryId,
            double score
    ) {
    }

    private List<Vector> index =
            List.of();

    /** idf đã tính trên toàn bộ câu hỏi khi lập chỉ mục. */
    private Map<String, Double> idf =
            Map.of();

    private boolean ready =
            false;

    /** Kết quả truy vấn. */
    public record Result(
            double score,
            Status status,
            FaqEntry entry,
            List<FaqEntry> suggestions
    ) {

        /** Trả lời trực tiếp được. */
        public boolean hit() {
            return status == Status.HIT;
        }

        /** Chỉ nên hiện chip gợi ý. */
        public boolean suggest() {
            return status == Status.SUGGEST;
        }
    }

    public enum Status {

        /** Điểm ≥ {@link FaqRetriever#THRESHOLD_ANSWER}. */
        HIT,

        /** Điểm trong khoảng {@code [THRESHOLD_SUGGEST, THRESHOLD_ANSWER)}. */
        SUGGEST,

        /** Điểm < {@link FaqRetriever#THRESHOLD_SUGGEST} hoặc bị lọc. */
        MISS
    }

    /** Nạp kho và (lập lại) chỉ mục. Gọi khi khởi động / khi file đổi. */
    @PostConstruct
    public void load() {

        if (knowledgeBase.size() == 0) {
            knowledgeBase.load();
        }

        buildIndex();

        ready = true;
    }

    private void buildIndex() {

        List<FaqEntry> entries =
                knowledgeBase.entries()
                        .stream()
                        .filter(FaqEntry::retrievable)
                        .toList();

        List<String> questions =
                new ArrayList<>();

        for (FaqEntry entry : entries) {
            questions.addAll(entry.questions());
        }

        // Document frequency cho từng feature.
        Map<String, Integer> documentFrequency =
                new HashMap<>();

        List<Map<String, Double>> rawVectors =
                new ArrayList<>(questions.size());

        for (String question : questions) {

            Map<String, Double> features =
                    features(question);

            rawVectors.add(features);

            for (String feature : features.keySet()) {

                documentFrequency.merge(
                        feature,
                        1,
                        Integer::sum
                );
            }
        }

        int total =
                rawVectors.size();

        Map<String, Double> weights =
                new HashMap<>();

        for (Map.Entry<String, Integer> entry :
                documentFrequency.entrySet()) {

            double idfValue =
                    Math.log(
                            (total + 1.0)
                                    / (entry.getValue() + 1.0)
                    ) + 1.0;

            weights.put(
                    entry.getKey(),
                    idfValue
            );
        }

        idf =
                weights;

        List<Vector> vectors =
                new ArrayList<>();

        int position =
                0;

        for (FaqEntry entry : entries) {

            for (String question : entry.questions()) {

                Map<String, Double> vector =
                        normalize(
                                weight(
                                        rawVectors.get(position++)
                                )
                        );

                if (!vector.isEmpty()) {

                    vectors.add(
                            new Vector(
                                    entry.id(),
                                    vector
                            )
                    );
                }
            }
        }

        index =
                List.copyOf(vectors);

        log.info(
                "Chỉ mục FAQ: {} vector / {} mục / {} feature",
                index.size(),
                entries.size(),
                idf.size()
        );
    }

    /**
     * Truy vấn kho.
     *
     * @param raw  câu người dùng (chưa chuẩn hóa)
     * @param role vai trò hiện tại - mục không thuộc vai trò bị loại
     */
    public Result retrieve(
            String raw,
            RoleCode role
    ) {

        List<Ranked> ranked =
                ranked(raw, role);

        if (ranked.isEmpty()) {

            return new Result(
                    0.0,
                    Status.MISS,
                    null,
                    List.of()
            );
        }

        double topScore =
                ranked.get(0).score();

        if (topScore >= THRESHOLD_ANSWER) {

            return new Result(
                    topScore,
                    Status.HIT,
                    entryById(ranked.get(0).entryId()),
                    List.of()
            );
        }

        if (topScore >= THRESHOLD_SUGGEST) {

            List<FaqEntry> suggestions =
                    new ArrayList<>();

            for (Ranked line : ranked) {

                if (line.score() < THRESHOLD_SUGGEST) {
                    break;
                }

                suggestions.add(
                        entryById(line.entryId())
                );

                if (suggestions.size() >= MAX_CHIPS) {
                    break;
                }
            }

            return new Result(
                    topScore,
                    Status.SUGGEST,
                    suggestions.get(0),
                    List.copyOf(suggestions)
            );
        }

        return new Result(
                topScore,
                Status.MISS,
                null,
                List.of()
        );
    }

    /**
     * {@code n} mục được xếp hạng cao nhất cho câu hỏi này — dùng cho đo
     * recall@k. Không áp ngưỡng: câu nào cũng có bảng xếp hạng trả về.
     *
     * @param raw   câu người dùng
     * @param role  vai trò hiện tại
     * @param limit số mục tối đa trả về
     */
    public List<FaqEntry> top(
            String raw,
            RoleCode role,
            int limit
    ) {

        List<FaqEntry> result =
                new ArrayList<>();

        for (Ranked line : ranked(raw, role)) {

            if (result.size() >= limit) {
                break;
            }

            result.add(
                    entryById(line.entryId())
            );
        }

        return result;
    }

    /** Bảng xếp hạng đầy đủ đã lọc vai trò + PENDING_FEATURE. */
    private List<Ranked> ranked(
            String raw,
            RoleCode role
    ) {

        if (!ready) {
            load();
        }

        String queryPlain =
                TextNormalizer.toPlain(raw);

        Map<String, Double> query =
                weight(
                        features(raw)
                );

        if (query.isEmpty()) {
            return List.of();
        }

        normalizeInPlace(query);

        Map<String, Double> best =
                new LinkedHashMap<>();

        for (Vector vector : index) {

            double cosine =
                    dot(
                            query,
                            vector.weights()
                    );

            Double previous =
                    best.get(
                            vector.entryId()
                    );

            if (previous == null
                    || cosine > previous) {

                best.put(
                        vector.entryId(),
                        cosine
                );
            }
        }

        Set<String> queryTags =
                queryTags(queryPlain);

        List<Ranked> lines =
                new ArrayList<>();

        for (Map.Entry<String, Double> entry :
                best.entrySet()) {

            FaqEntry faq =
                    knowledgeBase.byId(entry.getKey());

            if (faq == null
                    || !faq.retrievable()
                    || !faq.allows(role)) {
                continue;
            }

            double score =
                    entry.getValue();

            if (!queryTags.isEmpty()
                    && faq.tags() != null
                    && faq.tags().stream().anyMatch(
                    queryTags::contains
            )) {

                score += TAG_BONUS;
            }

            lines.add(
                    new Ranked(
                            entry.getKey(),
                            Math.min(1.0, score)
                    )
            );
        }

        lines.sort(
                Comparator.comparingDouble(
                        Ranked::score
                ).reversed()
        );

        return lines;
    }

    private FaqEntry entryById(
            String id
    ) {
        return knowledgeBase.byId(id);
    }

    /**
     * Các tag xuất hiện trong truy vấn — tag khớp theo <b>ranh giới khoảng
     * trắng</b> của {@code plain} nên tag nhiều từ cũng trúng.
     */
    private Set<String> queryTags(
            String queryPlain
    ) {

        Set<String> matched =
                new HashSet<>();

        if (queryPlain == null
                || queryPlain.isEmpty()) {
            return matched;
        }

        String padded =
                " " + queryPlain + " ";

        Set<String> distinctTags =
                new LinkedHashSet<>();

        for (FaqEntry entry : knowledgeBase.entries()) {

            if (entry.tags() != null) {
                distinctTags.addAll(entry.tags());
            }
        }

        for (String tag : distinctTags) {

            String plainTag =
                    TextNormalizer.toPlain(tag)
                            .trim();

            if (plainTag.isEmpty()) {
                continue;
            }

            if (padded.contains(" " + plainTag + " ")) {
                matched.add(tag);
            }
        }

        return matched;
    }

    // ------------------------------------------------------------------
    // TF-IDF
    // ------------------------------------------------------------------

    /** Bóc feature: word 1–2-gram + char 3–5-gram (tần suất thô). */
    static Map<String, Double> features(
            String text
    ) {

        String plain =
                TextNormalizer.toPlain(text);

        Map<String, Double> counts =
                new HashMap<>();

        if (plain.isEmpty()) {
            return counts;
        }

        String[] tokens =
                plain.split(" ");

        for (int n = WORD_N_MIN; n <= WORD_N_MAX; n++) {

            for (int i = 0; i + n <= tokens.length; i++) {

                StringBuilder builder =
                        new StringBuilder();

                for (int k = 0; k < n; k++) {

                    if (k > 0) {
                        builder.append('_');
                    }

                    builder.append(tokens[i + k]);
                }

                merge(
                        counts,
                        "w:" + builder
                );
            }
        }

        String padded =
                " " + plain + " ";

        for (int n = CHAR_N_MIN; n <= CHAR_N_MAX; n++) {

            for (int i = 0; i + n <= padded.length(); i++) {

                merge(
                        counts,
                        "c:" + padded.substring(i, i + n)
                );
            }
        }

        return counts;
    }

    private static void merge(
            Map<String, Double> counts,
            String feature
    ) {
        counts.merge(
                feature,
                1.0,
                Double::sum
        );
    }

    /** Nhân tần suất với idf; bỏ feature lạ (không đối chiếu được). */
    private Map<String, Double> weight(
            Map<String, Double> counts
    ) {

        Map<String, Double> weights =
                new HashMap<>();

        for (Map.Entry<String, Double> entry :
                counts.entrySet()) {

            Double idfValue =
                    idf.get(entry.getKey());

            if (idfValue == null) {
                continue;
            }

            double tf =
                    1.0 + Math.log(entry.getValue());

            weights.put(
                    entry.getKey(),
                    tf * idfValue
            );
        }

        return weights;
    }

    private static void normalizeInPlace(
            Map<String, Double> weights
    ) {

        double norm =
                0.0;

        for (double value : weights.values()) {
            norm += value * value;
        }

        if (norm <= 0.0) {
            return;
        }

        double scale =
                1.0 / Math.sqrt(norm);

        for (Map.Entry<String, Double> entry :
                weights.entrySet()) {

            entry.setValue(
                    entry.getValue() * scale
            );
        }
    }

    private static Map<String, Double> normalize(
            Map<String, Double> weights
    ) {
        normalizeInPlace(weights);
        return weights;
    }

    private static double dot(
            Map<String, Double> left,
            Map<String, Double> right
    ) {

        if (left.size() > right.size()) {
            return dot(right, left);
        }

        double sum =
                0.0;

        for (Map.Entry<String, Double> entry :
                left.entrySet()) {

            Double other =
                    right.get(entry.getKey());

            if (other != null) {
                sum += entry.getValue() * other;
            }
        }

        return sum;
    }

    /** Số vector trong chỉ mục (dùng cho test/báo cáo). */
    public int indexSize() {
        return index.size();
    }

    public boolean ready() {
        return ready;
    }
}
