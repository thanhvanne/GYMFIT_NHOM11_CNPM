package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gymfit.chat.nlu.Intent;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Trạng thái hội thoại của một phiên chat — serialize bằng Jackson vào
 * {@code chat_session.state_json}.
 *
 * <p>Quy tắc:
 * <ul>
 *     <li>Quá {@code session-idle-minutes} → reset toàn bộ.</li>
 *     <li>Quá {@code pending-action-ttl-minutes} → xoá {@code pending}.</li>
 * </ul>
 */
@Getter
@Setter
@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class ConversationState {

    /** Intent đang xử lý (để hỏi tiếp theo ngữ cảnh). */
    private Intent activeIntent;

    /** Bot đang chờ gì. */
    private Awaiting awaiting =
            Awaiting.NONE;

    /** Slot đã điền: service, date, time, facilityId, bookingId… */
    private Map<String, String> slots =
            new HashMap<>();

    /** Hành động chờ xác nhận. */
    private PendingAction pending;

    /** Intent của lượt trước — dùng cho câu hỏi tiếp theo kiểu "còn Q7 thì sao?". */
    private Intent lastIntent;

    /** Slot của lượt trước. */
    private Map<String, String> lastSlots =
            new HashMap<>();

    private Instant updatedAt =
            Instant.now();

    public ConversationState() {
    }

    public ConversationState(
            Instant updatedAt
    ) {
        this.updatedAt =
                updatedAt;
    }

    /**
     * Reset về trạng thái rỗng (khi hết idle).
     */
    public void reset(
            Instant now
    ) {
        this.activeIntent =
                null;

        this.awaiting =
                Awaiting.NONE;

        this.slots =
                new HashMap<>();

        this.pending =
                null;

        this.lastIntent =
                null;

        this.lastSlots =
                new HashMap<>();

        this.updatedAt =
                now;
    }

    /**
     * Dọn trạng thái hết hạn theo cấu hình.
     */
    public void expireIfNeeded(
            Instant now,
            int idleMinutes,
            int pendingTtlMinutes
    ) {

        if (updatedAt != null
                && idleMinutes > 0
                && now.isAfter(
                updatedAt.plusSeconds(
                        idleMinutes * 60L
                )
        )) {
            reset(now);

            return;
        }

        if (pending != null
                && pending.isExpired(now)) {
            pending =
                    null;

            awaiting =
                    Awaiting.NONE;
        }

        updatedAt =
                now;
    }

    public String slot(
            String key
    ) {
        return slots == null
                ? null
                : slots.get(key);
    }

    public void put(
            String key,
            String value
    ) {
        if (slots == null) {
            slots =
                    new HashMap<>();
        }

        if (value == null) {
            slots.remove(key);
        } else {
            slots.put(
                    key,
                    value
            );
        }
    }

    /** Ghi nhớ intent + slot của lượt này để hỏi tiếp theo ngữ cảnh. */
    public void remember(
            Intent intent
    ) {
        this.lastIntent =
                intent;

        this.lastSlots =
                slots == null
                        ? new HashMap<>()
                        : new HashMap<>(slots);
    }

    public boolean isEmpty() {
        return activeIntent == null
                && awaiting == Awaiting.NONE
                && (slots == null || slots.isEmpty())
                && pending == null;
    }
}