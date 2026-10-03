package com.gymfit.chat.dto;

import java.util.List;

/**
 * Thẻ hiển thị cho người dùng.
 *
 * @param type           {@code CONFIRM} (cần Xác nhận / Hủy) hoặc {@code LIST}
 * @param title          tiêu đề thẻ
 * @param lines          các dòng chi tiết
 * @param confirmPayload payload gửi khi bấm Xác nhận
 * @param cancelPayload  payload gửi khi bấm Hủy
 */
public record ChatCard(
        String type,
        String title,
        List<ChatCardLine> lines,
        String confirmPayload,
        String cancelPayload
) {

    public static final String TYPE_CONFIRM =
            "CONFIRM";

    public static final String TYPE_LIST =
            "LIST";

    public static ChatCard confirm(
            String title,
            List<ChatCardLine> lines,
            String confirmPayload,
            String cancelPayload
    ) {
        return new ChatCard(
                TYPE_CONFIRM,
                title,
                lines == null ? List.of() : List.copyOf(lines),
                confirmPayload,
                cancelPayload
        );
    }

    public static ChatCard list(
            String title,
            List<ChatCardLine> lines
    ) {
        return new ChatCard(
                TYPE_LIST,
                title,
                lines == null ? List.of() : List.copyOf(lines),
                null,
                null
        );
    }
}