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
    /** Kho FAQ tổng hợp (≥ 70 mục) - đích của mọi intent FAQ gộp. */
    FAQ_GENERAL,

    /** @deprecated Gộp vào {@link #FAQ_GENERAL} - xem {@code intent-aliases.json}. */
    @Deprecated
    FAQ_CANCEL_POLICY,
    /** @deprecated Gộp vào {@link #FAQ_GENERAL} - xem {@code intent-aliases.json}. */
    @Deprecated
    FAQ_BOOKING_RULES,
    /** @deprecated Gộp vào {@link #FAQ_GENERAL} - xem {@code intent-aliases.json}. */
    @Deprecated
    FAQ_CHECKIN_HOWTO,
    /** @deprecated Gộp vào {@link #FAQ_GENERAL} - xem {@code intent-aliases.json}. */
    @Deprecated
    FAQ_BUY_PLAN_HOWTO,
    /** @deprecated Gộp vào {@link #FAQ_GENERAL} - xem {@code intent-aliases.json}. */
    @Deprecated
    FAQ_CHECKIN_REJECTED,
    /** @deprecated Gộp vào {@link #FAQ_GENERAL} - xem {@code intent-aliases.json}. */
    @Deprecated
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

    /**
     * 6 intent FAQ cũ đã gộp vào {@link #FAQ_GENERAL} - vẫn giữ trong enum
     * để {@code Intent.valueOf} không vỡ với nhãn cũ còn trong DB
     * (candidate/holdout), nhưng <b>không còn là nhãn mô hình</b>.
     */
    private static final java.util.Set<Intent> MERGED_FAQ =
            java.util.Set.of(
                    FAQ_CANCEL_POLICY,
                    FAQ_BOOKING_RULES,
                    FAQ_CHECKIN_HOWTO,
                    FAQ_BUY_PLAN_HOWTO,
                    FAQ_CHECKIN_REJECTED,
                    FAQ_QR_HOWTO
            );

    /** Intent đã bị gộp - không còn xuất hiện trong dataset/model mới. */
    public boolean isMergedFaq() {
        return MERGED_FAQ.contains(this);
    }

    /**
     * Nhãn dùng cho dataset/model/report: các intent gộp trả về
     * {@link #FAQ_GENERAL}, các intent còn lại trả về {@link #name()}.
     */
    public String label() {
        return isMergedFaq()
                ? FAQ_GENERAL.name()
                : name();
    }

    /** Số intent thực sự là nhãn mô hình (loại intent đã gộp). */
    public static int activeCount() {
        int count = 0;

        for (Intent intent : values()) {
            if (!intent.isMergedFaq()) {
                count++;
            }
        }

        return count;
    }

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

            case FAQ_GENERAL,
                 FAQ_CANCEL_POLICY, FAQ_BOOKING_RULES,
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