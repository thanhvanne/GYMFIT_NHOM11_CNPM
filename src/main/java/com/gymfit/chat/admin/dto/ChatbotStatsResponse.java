package com.gymfit.chat.admin.dto;

import java.util.List;

/**
 * Thống kê cho trang quản trị chatbot.
 *
 * <p><b>Cách tính (ghi rõ để không hiểu nhầm số liệu):</b>
 * <ul>
 *   <li>{@code messagesLast24h} – số tin của <b>người dùng</b> trong 24 giờ qua.</li>
 *   <li>{@code fallbackLast24h} – câu trả lời bot rơi vào nhánh fallback
 *       = dự đoán {@code OUT_OF_SCOPE} <b>hoặc</b> độ tin cậy &lt;
 *       {@code threshold-clarify} (cùng định nghĩa với
 *       {@code DialogueManager.gate}).</li>
 *   <li>{@code fallbackRate} = fallback / số câu trả lời bot trong 24 giờ qua.</li>
 *   <li>{@code thumbsUp/thumbsDown} – tổng 👍/👎 toàn bộ lịch sử.</li>
 *   <li>{@code topFallbackTexts} – tối đa 20 câu {@code PENDING} mới nhất
 *       (chính là đầu vào cho vòng gán nhãn).</li>
 * </ul>
 *
 * @param messagesLast24h     số tin người dùng trong 24 giờ qua
 * @param botRepliesLast24h   số câu trả lời bot trong 24 giờ qua
 * @param fallbackLast24h     số câu trả lời rơi vào fallback trong 24 giờ qua
 * @param fallbackRate        tỷ lệ fallback, làm tròn 4 chữ số
 * @param candidatesPending   ứng viên chờ gán nhãn
 * @param candidatesLabeled   ứng viên đã gán nhãn
 * @param candidatesRejected  ứng viên bị loại
 * @param thumbsUp            tổng số 👍
 * @param thumbsDown          tổng số 👎
 * @param dislikeRate         tỷ lệ 👎 = down / (up + down)
 * @param feedbackByIntent    👍/👎 theo từng intent (sắp theo 👎 giảm dần)
 * @param topFallbackTexts    20 câu fallback mới nhất
 */
public record ChatbotStatsResponse(

        long messagesLast24h,

        long botRepliesLast24h,

        long fallbackLast24h,

        double fallbackRate,

        long candidatesPending,

        long candidatesLabeled,

        long candidatesRejected,

        long thumbsUp,

        long thumbsDown,

        double dislikeRate,

        List<FeedbackCountResponse> feedbackByIntent,

        List<String> topFallbackTexts
) {
}
