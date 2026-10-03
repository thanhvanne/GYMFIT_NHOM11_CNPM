package com.gymfit.chat.nlu;

import java.util.List;

/**
 * Kết quả dự đoán intent.
 *
 * @param intent       intent dự đoán chính
 * @param confidence   độ tin cậy 0..1
 * @param alternatives các intent còn lại theo thứ tự giảm dần
 * @param fromRule     true nếu do rules guard quyết định (không qua mô hình)
 */
public record IntentPrediction(
        Intent intent,
        double confidence,
        List<Intent> alternatives,
        boolean fromRule
) {

    public static IntentPrediction rule(
            Intent intent
    ) {
        return new IntentPrediction(
                intent,
                1.0,
                List.of(),
                true
        );
    }

    /** Lấy mô tả tiếng Việt của intent (dùng cho câu hỏi làm rõ). */
    public static String label(
            Intent intent
    ) {
        return intent == null
                ? ""
                : IntentLabels.describe(intent);
    }

}