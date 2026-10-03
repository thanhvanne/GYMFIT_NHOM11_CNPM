package com.gymfit.chat.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gymfit.chat.dialogue.Awaiting;
import com.gymfit.chat.dialogue.ConversationState;
import com.gymfit.chat.dialogue.PendingAction;
import com.gymfit.chat.nlu.Intent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serialize / deserialize {@link ConversationState} và kiểm tra luật hết hạn.
 */
class ConversationStateJsonTest {

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(
                            new JavaTimeModule()
                    );

    @Test
    @DisplayName("Serialize → deserialize giữ nguyên toàn bộ state")
    void roundTrip() throws Exception {

        ConversationState state =
                new ConversationState(
                        Instant.parse(
                                "2026-10-03T03:00:00Z"
                        )
                );

        state.setActiveIntent(Intent.BOOKING_CREATE);
        state.setAwaiting(Awaiting.TIME);

        state.put("service", "GYM");
        state.put("date", "2026-10-04");
        state.put("branchId", "2");

        state.setPending(
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CREATE,
                        "{\"facilityId\":5}",
                        Instant.parse(
                                "2026-10-03T03:05:00Z"
                        )
                )
        );

        state.remember(
                Intent.BOOKING_AVAILABILITY
        );

        String json =
                mapper.writeValueAsString(
                        state
                );

        ConversationState restored =
                mapper.readValue(
                        json,
                        ConversationState.class
                );

        assertEquals(
                Intent.BOOKING_CREATE,
                restored.getActiveIntent()
        );

        assertEquals(
                Awaiting.TIME,
                restored.getAwaiting()
        );

        assertEquals(
                "GYM",
                restored.slot("service")
        );

        assertEquals(
                "2026-10-04",
                restored.slot("date")
        );

        assertEquals(
                "2",
                restored.slot("branchId")
        );

        assertNotNull(
                restored.getPending()
        );

        assertEquals(
                PendingAction.TYPE_BOOKING_CREATE,
                restored.getPending()
                        .type()
        );

        assertEquals(
                Intent.BOOKING_AVAILABILITY,
                restored.getLastIntent()
        );
    }

    @Test
    @DisplayName("State mặc định: chưa chờ gì, chưa có slot")
    void defaults() {

        ConversationState state =
                new ConversationState(
                        Instant.now()
                );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );

        assertNull(
                state.getPending()
        );

        assertNull(
                state.getActiveIntent()
        );

        assertTrue(
                state.isEmpty()
        );
    }

    @Test
    @DisplayName("Quá idle-minutes → reset toàn bộ")
    void idleExpiry() {

        Instant updated =
                Instant.parse(
                        "2026-10-03T03:00:00Z"
                );

        ConversationState state =
                new ConversationState(updated);

        state.setActiveIntent(Intent.BOOKING_CREATE);
        state.setAwaiting(Awaiting.CONFIRM);
        state.put("service", "GYM");

        state.expireIfNeeded(
                updated.plus(
                        31,
                        ChronoUnit.MINUTES
                ),
                30,
                5
        );

        assertNull(
                state.getActiveIntent()
        );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );

        assertTrue(
                state.slot("service") == null
        );

        assertTrue(
                state.isEmpty()
        );
    }

    @Test
    @DisplayName("Chưa quá idle → giữ nguyên state")
    void notExpiredYet() {

        Instant updated =
                Instant.parse(
                        "2026-10-03T03:00:00Z"
                );

        ConversationState state =
                new ConversationState(updated);

        state.setActiveIntent(Intent.BOOKING_CREATE);
        state.setAwaiting(Awaiting.SERVICE);

        state.expireIfNeeded(
                updated.plus(
                        5,
                        ChronoUnit.MINUTES
                ),
                30,
                5
        );

        assertEquals(
                Intent.BOOKING_CREATE,
                state.getActiveIntent()
        );

        assertEquals(
                Awaiting.SERVICE,
                state.getAwaiting()
        );
    }

    @Test
    @DisplayName("Pending hết hạn → xoá pending và thoát chờ xác nhận")
    void pendingExpiry() {

        Instant updated =
                Instant.parse(
                        "2026-10-03T03:00:00Z"
                );

        ConversationState state =
                new ConversationState(updated);

        state.setAwaiting(Awaiting.CONFIRM);

        state.setPending(
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CANCEL,
                        "{\"bookingId\":9}",
                        updated.plus(
                                4,
                                ChronoUnit.MINUTES
                        )
                )
        );

        state.expireIfNeeded(
                updated.plus(
                        6,
                        ChronoUnit.MINUTES
                ),
                30,
                5
        );

        assertNull(
                state.getPending()
        );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );
    }

    @Test
    @DisplayName("Pending còn hạn → giữ nguyên")
    void pendingNotExpired() {

        Instant updated =
                Instant.parse(
                        "2026-10-03T03:00:00Z"
                );

        ConversationState state =
                new ConversationState(updated);

        state.setAwaiting(Awaiting.CONFIRM);

        PendingAction action =
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CREATE,
                        "{}",
                        updated.plus(
                                4,
                                ChronoUnit.MINUTES
                        )
                );

        state.setPending(
                action
        );

        state.expireIfNeeded(
                updated.plus(
                        1,
                        ChronoUnit.MINUTES
                ),
                30,
                5
        );

        assertNotNull(
                state.getPending()
        );

        assertEquals(
                Awaiting.CONFIRM,
                state.getAwaiting()
        );
    }

    @Test
    @DisplayName("PendingAction.matches() chỉ đúng khi id khớp")
    void pendingMatches() {

        PendingAction action =
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CREATE,
                        "{}",
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        assertTrue(
                action.matches(
                        action.id()
                )
        );

        assertFalse(
                action.matches(
                        "khac"
                )
        );

        assertFalse(
                action.matches(
                        null
                )
        );
    }

    @Test
    @DisplayName("remember() lưu lại intent + slot của lượt trước")
    void remember() {

        ConversationState state =
                new ConversationState(
                        Instant.now()
                );

        state.put("branchId", "2");
        state.put("service", "BOXING");

        state.remember(
                Intent.BRANCH_INFO
        );

        state.put("service", "GYM");

        assertEquals(
                Intent.BRANCH_INFO,
                state.getLastIntent()
        );

        assertEquals(
                "BOXING",
                state.getLastSlots()
                        .get("service")
        );

        assertEquals(
                "GYM",
                state.slot("service")
        );
    }

    @Test
    @DisplayName("JSON có trường lạ vẫn deserialize được")
    void unknownProperties() throws Exception {

        String json =
                "{\"awaiting\":\"DATE\",\"unknownField\":123,"
                        + "\"slots\":{\"service\":\"GYM\"}}";

        ConversationState state =
                mapper.readValue(
                        json,
                        ConversationState.class
                );

        assertEquals(
                Awaiting.DATE,
                state.getAwaiting()
        );

        assertEquals(
                "GYM",
                state.slot("service")
        );
    }

}