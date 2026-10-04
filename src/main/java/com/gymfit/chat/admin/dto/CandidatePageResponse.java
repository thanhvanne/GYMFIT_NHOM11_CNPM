package com.gymfit.chat.admin.dto;

import java.util.List;

/**
 * Trang danh sách ứng viên huấn luyện.
 *
 * @param total tổng số dòng
 * @param page  trang hiện tại (bắt đầu từ 0)
 * @param size  số dòng mỗi trang
 * @param items nội dung trang
 */
public record CandidatePageResponse(

        long total,

        int page,

        int size,

        List<CandidateResponse> items
) {
}
