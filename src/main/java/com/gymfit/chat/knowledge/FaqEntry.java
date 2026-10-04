package com.gymfit.chat.knowledge;

import com.gymfit.user.RoleCode;

import java.util.List;
import java.util.Set;

/**
 * Một mục trong kho FAQ {@code chatbot/faq_kb.json} (V2-3).
 *
 * <p>Theo plan 5.3, mỗi mục bắt buộc có {@code source} (lớp/method đã xác minh)
 * và ≥ 6 câu hỏi. {@code status} quyết định cách xử lý:
 *
 * <ul>
 *     <li>{@link #STATUS_ACTIVE} — trả lời bình thường;</li>
 *     <li>{@link #STATUS_NOT_SUPPORTED} — trả lời "hệ thống chưa hỗ trợ…"
 *         (nội dung nằm sẵn trong {@code answer});</li>
 *     <li>{@link #STATUS_PENDING_FEATURE} — <b>bị bỏ qua</b>, không bao giờ
 *         được retriever trả về.</li>
 * </ul>
 *
 * @param id        mã mục duy nhất, ví dụ {@code plan_diff_tiers}
 * @param roles     vai trò được phép xem (rỗng = mọi vai trò)
 * @param status    {@code ACTIVE} / {@code NOT_SUPPORTED} / {@code PENDING_FEATURE}
 * @param questions ≥ 6 câu hỏi khác cách nói, dùng để lập chỉ mục
 * @param answer    câu trả lời (chỉ viết khi đã xác minh trong code)
 * @param link      trang liên quan
 * @param source    nguồn xác minh: {@code Lớp.method}
 * @param tags      nhãn chủ đề — dùng cho bonus {@code +0.05} ở {@link FaqRetriever}
 */
public record FaqEntry(
        String id,
        Set<RoleCode> roles,
        String status,
        List<String> questions,
        String answer,
        String link,
        String source,
        List<String> tags
) {

    public static final String STATUS_ACTIVE =
            "ACTIVE";

    public static final String STATUS_NOT_SUPPORTED =
            "NOT_SUPPORTED";

    public static final String STATUS_PENDING_FEATURE =
            "PENDING_FEATURE";

    /** Mục được phép trả lời (không phải tính năng đang chờ). */
    public boolean retrievable() {
        return !STATUS_PENDING_FEATURE.equals(status);
    }

    /** Mục có đúng vai trò này (rỗng = mọi vai trò đều xem được). */
    public boolean allows(
            RoleCode role
    ) {
        return roles == null
                || roles.isEmpty()
                || roles.contains(role);
    }

    /** Câu hỏi đầu tiên — dùng làm nhãn chip gợi ý. */
    public String sampleQuestion() {
        return questions == null || questions.isEmpty()
                ? id
                : questions.get(0);
    }
}
