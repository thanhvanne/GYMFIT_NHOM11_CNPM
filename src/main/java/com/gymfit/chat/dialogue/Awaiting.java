package com.gymfit.chat.dialogue;

/**
 * Bot đang chờ người dùng cung cấp gì.
 */
public enum Awaiting {

    NONE,

    /** Chờ chọn dịch vụ tập */
    SERVICE,

    /** Chờ chọn ngày */
    DATE,

    /** Chờ chọn giờ */
    TIME,

    /** Chờ chọn 1 trong các lịch sắp tới (để hủy) */
    BOOKING_PICK,

    /** Chờ xác nhận thao tác ghi dữ liệu */
    CONFIRM,

    /** Chờ chọn dịch vụ khi gợi ý gói tập */
    PLAN_SERVICE,

    /** Chờ chọn ngân sách khi gợi ý gói tập */
    PLAN_BUDGET

}