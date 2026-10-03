package com.gymfit.chat.nlu;

import com.gymfit.chat.nlu.entity.Entities;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Phân loại intent cho một câu nói.
 *
 * <p>Thứ tự xử lý:
 * <ol>
 *     <li><b>Rules guard</b> — chỉ khi câu rất ngắn (≤ 4 token): chào / cảm ơn /
 *         tạm biệt / ok / không. Khớp trên dạng <b>giữ dấu</b> trước, không dấu sau
 *         (vì bỏ dấu làm "dừng" trùng "đúng").</li>
 *     <li>Câu rỗng hoặc chỉ ký tự đặc biệt → {@code OUT_OF_SCOPE} 1.0.</li>
 *     <li>Còn lại: mask → {@link FeatureExtractor} → {@link IntentModel} → top-3.</li>
 * </ol>
 *
 * <p>Ngưỡng accept/clarify do {@code DialogueManager} quyết định —
 * classifier chỉ trả dự đoán.
 */
@Component
@Slf4j
public class IntentClassifier {

    /** Số token tối đa để rules guard được phép quyết định. */
    public static final int RULE_MAX_TOKENS = 4;

    private static final Map<Intent, Pattern> RULES =
            buildRules();

    private static final Map<Intent, Pattern> PLAIN_RULES =
            buildPlainRules();

    private final ChatPipeline pipeline;
    private final ResourceLoader resourceLoader;
    private final String modelPath;

    private IntentModel model;

    public IntentClassifier(
            ChatPipeline pipeline,
            ResourceLoader resourceLoader,
            @Value("${gymfit.chatbot.model-path}") String modelPath
    ) {
        this.pipeline =
                pipeline;

        this.resourceLoader =
                resourceLoader;

        this.modelPath =
                modelPath;
    }

    @PostConstruct
    public void load() {
        this.model =
                loadModel(
                        resourceLoader,
                        modelPath
                );

        log.info(
                "Đã nạp mô hình intent: {} nhãn, {} đặc trưng từ {}",
                model.labelCount(),
                model.featureCount(),
                modelPath
        );
    }

    public IntentModel model() {
        return model;
    }

    public IntentPrediction classify(
            NormalizedText text,
            Entities entities
    ) {
        return classify(
                text,
                entities,
                com.gymfit.common.util.TimeUtil
                        .today()
        );
    }

    /**
     * Phân loại với ngày cố định (test được, không gọi đồng hồ hệ thống).
     */
    public IntentPrediction classify(
            NormalizedText text,
            Entities entities,
            java.time.LocalDate today
    ) {

        if (text == null
                || text.plain() == null
                || text.plain()
                        .isBlank()) {
            return IntentPrediction.rule(
                    Intent.OUT_OF_SCOPE
            );
        }

        String plain =
                text.plain();

        String soft =
                TextNormalizer.toSoft(
                        text.raw()
                );

        int tokens =
                soft.isBlank()
                        ? 0
                        : soft.split(" ").length;

        // ---- 1. Rules guard ----
        if (tokens > 0
                && tokens <= RULE_MAX_TOKENS) {

            Intent ruled =
                    rule(
                            soft,
                            plain
                    );

            if (ruled != null) {
                return IntentPrediction.rule(
                        ruled
                );
            }
        }

        // ---- 2. Mô hình ----
        String masked =
                pipeline.mask(
                        plain,
                        entities
                );

        double[] probs =
                model.probabilities(
                        masked
                );

        int best =
                0;

        for (int i = 1; i < probs.length; i++) {

            if (probs[i] > probs[best]) {
                best =
                        i;
            }
        }

        List<Intent> alternatives =
                new ArrayList<>();

        List<int[]> top =
                model.top(
                        masked,
                        3
                );

        for (int i = 1; i < top.size(); i++) {
            alternatives.add(
                    Intent.valueOf(
                            model.labels[top.get(i)[0]]
                    )
            );
        }

        return new IntentPrediction(
                Intent.valueOf(
                        model.labels[best]
                ),
                probs[best],
                List.copyOf(alternatives),
                false
        );
    }

    // ------------------------------------------------------------------
    // Rules guard
    // ------------------------------------------------------------------

    /**
     * @return intent nếu rules khớp, null nếu không
     */
    private static Intent rule(
            String soft,
            String plain
    ) {

        Intent matched =
                match(
                        RULES,
                        soft
                );

        if (matched != null) {
            return matched;
        }

        return match(
                PLAIN_RULES,
                plain
        );
    }

    private static Intent match(
            Map<Intent, Pattern> rules,
            String phrase
    ) {
        if (phrase == null
                || phrase.isBlank()) {
            return null;
        }

        for (Map.Entry<Intent, Pattern> entry
                : rules.entrySet()) {

            if (entry.getValue()
                    .matcher(phrase)
                    .matches()) {
                return entry.getKey();
            }
        }

        return null;
    }

    private static Map<Intent, Pattern> buildRules() {

        Map<Intent, Pattern> rules =
                new LinkedHashMap<>();

        rules.put(
                Intent.GREETING,
                Pattern.compile(
                        "^(xin chào|chào|hello|hi|alo|hey|chào bạn"
                                + "|hello bạn|chào em|chị chào|alo alo|a lô)$"
                )
        );

        rules.put(
                Intent.THANKS,
                Pattern.compile(
                        "^(cảm ơn|cảm ơn bạn|cảm ơn nhiều|cám ơn"
                                + "|thanks|thank you|thanks a lot|tks|tk|thank"
                                + "|đa tạ|đa tạ bạn)$"
                )
        );

        rules.put(
                Intent.GOODBYE,
                Pattern.compile(
                        "^(tạm biệt|bye|goodbye|hẹn gặp lại|hẹn lại"
                                + "|gặp lại sau)$"
                )
        );

        rules.put(
                Intent.CONFIRM_YES,
                Pattern.compile(
                        "^(ok|oke|okie|ok fine|o k|ừ|ừa|u|uh|có|có rồi"
                                + "|đúng|đúng rồi|chuẩn|chính xác"
                                + "|xác nhận|xác nhận đi|đồng ý|đồng ý rồi"
                                + "|đồng ý đó|chốt|chốt đi|vâng|vâng nhé"
                                + "|được rồi|yes|yeah|yep|yess|sure|proceed"
                                + "|confirm|làm đi|đi luôn|tiến hành đi"
                                + "|vui lòng làm tiếp|gửi đi|next"
                                + "|yes please)$"
                )
        );

        rules.put(
                Intent.CONFIRM_NO,
                Pattern.compile(
                        "^(không|không nhé|không đâu|ko|k|thôi|thôi vậy"
                                + "|hủy bỏ|huỷ bỏ|bỏ qua|bỏ đi|dừng"
                                + "|dừng lại|khỏi|không cần|không cần nữa"
                                + "|bỏ qua nhé|no|nope|nah|never mind"
                                + "|nevermind|skip|cancel|thoát|hủy|huỷ"
                                + "|không làm|không đặt|không mua"
                                + "|đổi ý rồi|thôi đổi ý|tạm để sau"
                                + "|để sau đã|chưa giờ|stop|dừng thôi)$"
                )
        );

        return rules;
    }

    private static Map<Intent, Pattern> buildPlainRules() {

        Map<Intent, Pattern> plain =
                new LinkedHashMap<>();

        RULES.forEach((intent, pattern) ->
                plain.put(
                        intent,
                        Pattern.compile(
                                TextNormalizer.stripDiacritics(
                                        pattern.pattern()
                                )
                        )
                )
        );

        return plain;
    }

    // ------------------------------------------------------------------
    // Nạp model
    // ------------------------------------------------------------------

    static IntentModel loadModel(
            ResourceLoader resourceLoader,
            String path
    ) {

        try {

            String location =
                    path.startsWith(
                            "classpath:"
                    )
                            ? path.substring(
                            "classpath:".length()
                    )
                            : path;

            try (InputStream input =
                         resourceLoader.getResource(
                                         location
                                 )
                                 .getInputStream()) {

                return IntentModel.load(
                        input
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Không nạp được mô hình intent từ {}. "
                            + "Hãy chạy: mvn -q exec:java@train ({})",
                    path,
                    exception.getMessage()
            );

            throw new IllegalStateException(
                    "Không nạp được mô hình intent từ " + path,
                    exception
            );
        }
    }

}