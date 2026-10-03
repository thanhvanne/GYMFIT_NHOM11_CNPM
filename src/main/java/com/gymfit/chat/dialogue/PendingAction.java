package com.gymfit.chat.dialogue;

import java.time.Instant;
import java.util.UUID;

/**
 * Hành động đang chờ xác nhận.
 * <p>Chỉ được thực thi khi {@code id} khớp với {@code state.pending.id} và
 * chưa hết hạn.
 *
 * @param id          UUID sinh ra
 * @param type        loại hành động (BOOKING_CREATE / BOOKING_CANCEL)
 * @param payloadJson dữ liệu đã chuẩn hóa để deserialize
 * @param expiresAtUtc thời điểm hết hạn
 */
public record PendingAction(
        String id,
        String type,
        String payloadJson,
        Instant expiresAtUtc
) {

    public static final String TYPE_BOOKING_CREATE =
            "BOOKING_CREATE";

    public static final String TYPE_BOOKING_CANCEL =
            "BOOKING_CANCEL";

    public static PendingAction of(
            String type,
            String payloadJson,
            Instant expiresAtUtc
    ) {
        return new PendingAction(
                UUID.randomUUID()
                        .toString(),
                type,
                payloadJson,
                expiresAtUtc
        );
    }

    public boolean isExpired(
            Instant now
    ) {
        return expiresAtUtc == null
                || !now.isBefore(expiresAtUtc);
    }

    public boolean matches(
            String otherId
    ) {
        return id != null
                && id.equals(otherId);
    }
}