package com.gymfit.chat.nlu;

/**
 * Danh sách intent của chatbot (36).
 * <p>Giá trị lưu trong DB/dataset là {@link #name()}.
 */
public enum Intent {

    // ---- Chung ----
    GREETING,
    THANKS,
    GOODBYE,
    HELP,
    OUT_OF_SCOPE,
    CONFIRM_YES,
    CONFIRM_NO,

    // ---- Thông tin ----
    BRANCH_INFO,
    OPERATING_HOURS,
    LIST_SERVICES,
    LIST_FACILITIES,
    LIST_PLANS,
    PLAN_DETAIL,
    PLAN_COMPARE,
    PLAN_RECOMMEND,
    LIST_PRODUCTS,

    // ---- FAQ ----
    FAQ_CANCEL_POLICY,
    FAQ_BOOKING_RULES,
    FAQ_CHECKIN_HOWTO,
    FAQ_BUY_PLAN_HOWTO,
    FAQ_CHECKIN_REJECTED,
    FAQ_QR_HOWTO,

    // ---- Slot ----
    BOOKING_AVAILABILITY,

    // ---- Hội viên ----
    MY_MEMBERSHIP,
    MY_BOOKINGS,
    MY_CHECKINS,
    MY_ORDERS,
    BOOKING_CREATE,
    BOOKING_CANCEL,

    // ---- Vận hành ----
    REPORT_DASHBOARD,
    REPORT_REVENUE,
    REPORT_SERVICE,
    LOW_STOCK,
    BOOKINGS_TODAY,
    CHECKINS_REJECTED,

    // ---- Quản trị ----
    AUDIT_RECENT;

    /** Nhóm dùng để gom nhãn trong báo cáo. */
    public String group() {
        return switch (this) {
            case GREETING, THANKS, GOODBYE, HELP, OUT_OF_SCOPE,
                 CONFIRM_YES, CONFIRM_NO ->
                    "SMALLTALK";

            case BRANCH_INFO, OPERATING_HOURS, LIST_SERVICES,
                 LIST_FACILITIES, LIST_PLANS, PLAN_DETAIL,
                 PLAN_COMPARE, PLAN_RECOMMEND, LIST_PRODUCTS ->
                    "INFO";

            case FAQ_CANCEL_POLICY, FAQ_BOOKING_RULES,
                 FAQ_CHECKIN_HOWTO, FAQ_BUY_PLAN_HOWTO,
                 FAQ_CHECKIN_REJECTED, FAQ_QR_HOWTO ->
                    "FAQ";

            case BOOKING_AVAILABILITY ->
                    "SLOT";

            case MY_MEMBERSHIP, MY_BOOKINGS, MY_CHECKINS,
                 MY_ORDERS, BOOKING_CREATE, BOOKING_CANCEL ->
                    "MEMBER";

            case REPORT_DASHBOARD, REPORT_REVENUE, REPORT_SERVICE,
                 LOW_STOCK, BOOKINGS_TODAY, CHECKINS_REJECTED ->
                    "OPS";

            case AUDIT_RECENT ->
                    "ADMIN";
        };
    }

    /** Intent có thể dẫn tới thao tác ghi dữ liệu (cần xác nhận). */
    public boolean isAction() {
        return this == BOOKING_CREATE
                || this == BOOKING_CANCEL;
    }

}