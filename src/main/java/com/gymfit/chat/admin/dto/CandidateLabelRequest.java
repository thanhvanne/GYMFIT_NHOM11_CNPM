package com.gymfit.chat.admin.dto;

/**
 * Gán / đổi nhãn một ứng viên huấn luyện.
 *
 * <p>Không dùng {@code @Valid} cho nghiệp vụ chính: mã lỗi nghiệp vụ
 * ({@code candidate_label_required}, {@code candidate_label_unknown},
 * {@code candidate_status_invalid}) được kiểm tra trong service để trả về
 * đúng {@code code} cho front-end.
 *
 * @param label  nhãn intent (bắt buộc khi {@code status=LABELED})
 * @param status {@code PENDING | LABELED | REJECTED}
 */
public record CandidateLabelRequest(

        String label,

        String status
) {
}
