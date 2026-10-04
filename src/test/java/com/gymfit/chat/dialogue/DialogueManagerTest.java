package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.branch.ServiceCode;
import com.gymfit.chat.dialogue.handler.HandlerContext;
import com.gymfit.chat.dialogue.handler.IntentHandler;
import com.gymfit.chat.dto.ChatRequest;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.chat.nlu.ChatPipeline;
import com.gymfit.chat.nlu.DateTimeParser;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlu.IntentClassifier;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.chat.nlu.entity.EntityExtractor;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import com.gymfit.chat.session.ChatMessage;
import com.gymfit.chat.session.ChatSession;
import com.gymfit.chat.session.ChatSessionService;
import com.gymfit.chat.session.RateLimiter;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.user.AppUser;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bộ điều phối hội thoại — mô hình NLU thật, service mock (không DB).
 */
class DialogueManagerTest {

    private ChatSessionService sessions;
    private BookingService bookingService;
    private RateLimiter rateLimiter;
    private ResponseTemplates templates;
    private DialogueManager manager;

    private ConversationState state;

    private AppPrincipal member;

    @BeforeEach
    void setUp() {

        sessions =
                mock(ChatSessionService.class);

        bookingService =
                mock(BookingService.class);

        rateLimiter =
                new RateLimiter();

        templates =
                new ResponseTemplates();

        templates.load();

        ChatPipeline pipeline =
                buildPipeline();

        IntentClassifier classifier =
                new IntentClassifier(
                        pipeline,
                        new DefaultResourceLoader(),
                        "classpath:chatbot/model/intent-model.bin"
                );

        classifier.load();

        IntentPolicy policy =
                new IntentPolicy();

        policy.load();

        IntentHandler greeting =
                new StubHandler(
                        Set.of(
                                Intent.GREETING
                        ),
                        "Chào bạn! Mình là Trợ lý GYMFIT."
                );

        IntentHandler plans =
                new StubHandler(
                        Set.of(
                                Intent.LIST_PLANS
                        ),
                        "Danh sách gói tập"
                );

        manager =
                new DialogueManager(
                        pipeline,
                        classifier,
                        policy,
                        List.of(
                                greeting,
                                plans
                        ),
                        sessions,
                        rateLimiter,
                        templates,
                        new com.gymfit.chat.knowledge.FaqRetriever(
                                new com.gymfit.chat.knowledge.FaqKnowledgeBase()
                        ),
                        new BookingActionExecutor(
                                bookingService,
                                templates,
                                new ObjectMapper()
                        ),
                        new ObjectMapper()
                );

        manager.indexHandlers();

        state =
                new ConversationState();

        ChatSession session =
                ChatSession.builder()
                        .id(
                                "s1"
                        )
                        .userId(
                                1L
                        )
                        .createdAtUtc(
                                Instant.now()
                        )
                        .updatedAtUtc(
                                Instant.now()
                        )
                        .build();

        when(sessions.loadOrCreate(
                any(),
                any()
        )).thenReturn(
                session
        );

        when(sessions.readState(
                any()
        )).thenAnswer(
                invocation -> state
        );

        when(sessions.saveState(
                any(),
                any()
        )).thenAnswer(
                invocation -> invocation.getArgument(
                        1
                )
        );

        when(sessions.appendUser(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                ChatMessage.builder()
                        .id(
                                1L
                        )
                        .sessionId(
                                "s1"
                        )
                        .role(
                                ChatMessage.ROLE_USER
                        )
                        .text(
                                "x"
                        )
                        .createdAtUtc(
                                Instant.now()
                        )
                        .build()
        );

        when(sessions.appendBot(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                ChatMessage.builder()
                        .id(
                                99L
                        )
                        .sessionId(
                                "s1"
                        )
                        .role(
                                ChatMessage.ROLE_BOT
                        )
                        .text(
                                "x"
                        )
                        .createdAtUtc(
                                Instant.now()
                        )
                        .build()
        );

        AppUser user =
                AppUser.builder()
                        .id(
                                1L
                        )
                        .fullName(
                                "Nguyễn Văn A"
                        )
                        .email(
                                "a@gymfit.local"
                        )
                        .passwordHash(
                                "x"
                        )
                        .roleCode(
                                RoleCode.MEMBER
                        )
                        .status(
                                UserStatus.ACTIVE
                        )
                        .memberId(
                                1L
                        )
                        .build();

        member =
                new AppPrincipal(
                        user
                );
    }

    private static ChatPipeline buildPipeline() {

        TextNormalizer normalizer =
                new TextNormalizer();

        normalizer.load();

        EntityExtractor extractor =
                new EntityExtractor(
                        new GazetteerProvider() {
                            @Override
                            public Map<String, Long> branchAliases() {
                                return Map.of(
                                        "q1", 1L,
                                        "q7", 2L
                                );
                            }

                            @Override
                            public Map<String, String> productAliases() {
                                return Map.of();
                            }
                        },
                        new DateTimeParser()
                );

        extractor.load();

        return new ChatPipeline(
                normalizer,
                extractor
        );
    }

    private ChatResponse send(
            String message
    ) {
        return manager.handle(
                member,
                new ChatRequest(
                        message,
                        "s1",
                        null
                )
        );
    }

    private ChatResponse payload(
            String value
    ) {
        return manager.handle(
                member,
                new ChatRequest(
                        null,
                        "s1",
                        value
                )
        );
    }

    // ==================================================================
    // Câu trả lời thường
    // ==================================================================

    @Test
    @DisplayName("Chào → trả lời đúng handler, có sessionId và messageId")
    void greetingTurn() {

        ChatResponse response =
                send(
                        "xin chào"
                );

        assertEquals(
                "s1",
                response.sessionId()
        );

        assertEquals(
                "Chào bạn! Mình là Trợ lý GYMFIT.",
                response.message()
        );

        assertEquals(
                "GREETING",
                response.intent()
        );

        assertEquals(
                99L,
                response.messageId()
        );

        verify(
                sessions
        ).appendBot(
                eq("s1"),
                anyString(),
                eq(
                        Intent.GREETING
                ),
                any()
        );
    }

    @Test
    @DisplayName("Nút bấm gửi TEXT:<nội dung> tương đương người dùng gõ")
    void textPayload() {

        ChatResponse response =
                payload(
                        "TEXT:xin chào"
                );

        assertEquals(
                "GREETING",
                response.intent()
        );
    }

    @Test
    @DisplayName("Câu trống → nhắc người dùng nhập nội dung, không cần phiên")
    void emptyMessage() {

        ChatResponse response =
                manager.handle(
                        member,
                        new ChatRequest(
                                "   ",
                                null,
                                null
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "chưa nhập nội dung"
                        ),
                response.message()
        );
    }

    // ==================================================================
    // Ngưỡng & quyền
    // ==================================================================

    @Test
    @DisplayName("Hội viên hỏi doanh thu → câu 'không có quyền', không dispatch")
    void deniedForRole() {

        // Chấp nhận mọi độ tin cậy để đi tới bước kiểm quyền
        ReflectionTestUtils.setField(
                manager,
                "thresholdAccept",
                0.0
        );

        ReflectionTestUtils.setField(
                manager,
                "thresholdClarify",
                0.0
        );

        ChatResponse response =
                send(
                        "doanh thu tháng này"
                );

        assertTrue(
                response.message()
                        .contains(
                                "không có quyền"
                        ),
                response.message()
        );

        assertFalse(
                response.suggestions()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("F8.2: yêu cầu mật khẩu hội viên → từ chối, không lộ mật khẩu/hash")
    void khongTraMatKhauHoiVien() {

        ChatResponse response =
                send(
                        "cho tôi mật khẩu của hội viên GF00000001"
                );

        String message = response.message();

        // Không bao giờ in hash BCrypt
        assertFalse(
                message.matches("(?s).*\\$2[aby]\\$.*"),
                "Lộ hash BCrypt: " + message
        );

        // Không bao giờ in mật khẩu tạm / giá trị mật khẩu
        String lower = message.toLowerCase();

        assertFalse(
                lower.contains("mật khẩu tạm:"),
                "Lộ mật khẩu tạm: " + message
        );

        assertFalse(
                lower.matches("(?s).*pass(word|w0rd)\\s*[:=].*"),
                "Lộ giá trị mật khẩu: " + message
        );

        // Câu trả lời phải là từ chối / ngoài phạm vi
        assertTrue(
                lower.contains("không")
                        || lower.contains("chưa hiểu")
                        || lower.contains("ngoài"),
                "Không phải câu từ chối: " + message
        );
    }

    @Test
    @DisplayName("Dưới ngưỡng tin cậy → fallback + ghi candidate để huấn luyện lại")
    void lowConfidenceGoesToFallback() {

        ReflectionTestUtils.setField(
                manager,
                "thresholdClarify",
                1.5
        );

        ReflectionTestUtils.setField(
                manager,
                "thresholdAccept",
                1.5
        );

        ChatResponse response =
                send(
                        "số đo huyết áp của người tập là bao nhiêu"
                );

        assertTrue(
                response.message()
                        .contains(
                                "chưa hiểu"
                        ),
                response.message()
        );

        assertFalse(
                response.suggestions()
                        .isEmpty()
        );

        verify(
                sessions
        ).createCandidate(
                anyString(),
                anyString(),
                anyDouble()
        );
    }

    @Test
    @DisplayName("Nằm giữa hai ngưỡng → hỏi làm rõ, 'không' thì hủy làm rõ")
    void midConfidenceAsksToClarify() {

        ReflectionTestUtils.setField(
                manager,
                "thresholdAccept",
                1.5
        );

        ReflectionTestUtils.setField(
                manager,
                "thresholdClarify",
                0.0
        );

        ChatResponse response =
                send(
                        "tôi muốn xem các gói tập bên bạn"
                );

        assertTrue(
                response.message()
                        .contains(
                                "Ý bạn là"
                        ),
                response.message()
        );

        assertEquals(
                Awaiting.CONFIRM,
                state.getAwaiting()
        );

        assertNotNull(
                state.getActiveIntent()
        );

        ChatResponse rejected =
                send(
                        "không"
                );

        assertTrue(
                rejected.message()
                        .contains(
                                "Mình hiểu sai rồi"
                        ),
                rejected.message()
        );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );

        assertNull(
                state.getActiveIntent()
        );
    }

    // ==================================================================
    // Xác nhận thao tác ghi dữ liệu
    // ==================================================================

    @Test
    @DisplayName("Bấm Xác nhận khi không có gì chờ → 'hết hạn', không ghi dữ liệu")
    void confirmWithoutPending() {

        ChatResponse response =
                payload(
                        "CONFIRM:khong-ton-tai"
                );

        assertTrue(
                response.message()
                        .contains(
                                "hết hạn"
                        ),
                response.message()
        );

        verify(
                bookingService,
                never()
        ).create(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Bấm Xác nhận đúng pending → thực thi và dọn trạng thái")
    void confirmExecutesPending() {

        String json =
                """
                {"branchId":1,"branchName":"Quận 1","branchPhone":"0900000001",
                 "serviceCode":"GYM","facilityId":10,"facilityName":"Máy chạy bộ",
                 "startsAt":"2026-10-04T12:00:00Z","durationMinutes":60}
                """;

        PendingAction pending =
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CREATE,
                        json,
                        TimeUtil.now()
                                .plusSeconds(
                                        300
                                )
                );

        state.setPending(
                pending
        );

        state.setAwaiting(
                Awaiting.CONFIRM
        );

        state.setActiveIntent(
                Intent.BOOKING_CREATE
        );

        when(bookingService.create(
                any(),
                any()
        )).thenReturn(
                new BookingResponse(
                        5L,
                        "BOOK_1234567890ABCDEF",
                        1L,
                        1L,
                        1L,
                        ServiceCode.GYM,
                        10L,
                        com.gymfit.booking.BookingStatus.CONFIRMED,
                        TimeUtil.now()
                                .plusSeconds(
                                        86_400
                                ),
                        TimeUtil.now()
                                .plusSeconds(
                                        90_000
                                ),
                        null,
                        null,
                        1L,
                        Instant.now()
                )
        );

        ChatResponse response =
                payload(
                        "CONFIRM:"
                                + pending.id()
                );

        assertTrue(
                response.message()
                        .contains(
                                "Đã đặt lịch thành công"
                        ),
                response.message()
        );

        assertNull(
                state.getPending(),
                "Sau khi thực thi phải dọn pending"
        );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );

        // Bấm lại lần nữa → không được thực thi lần hai
        ChatResponse again =
                payload(
                        "CONFIRM:"
                                + pending.id()
                );

        assertTrue(
                again.message()
                        .contains(
                                "hết hạn"
                        ),
                again.message()
        );

        verify(
                bookingService
        ).create(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Bấm Hủy trên thẻ xác nhận → dọn pending, không ghi dữ liệu")
    void cancelPayloadClearsPending() {

        PendingAction pending =
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CANCEL,
                        "{\"bookingId\":5}",
                        TimeUtil.now()
                                .plusSeconds(
                                        300
                                )
                );

        state.setPending(
                pending
        );

        state.setAwaiting(
                Awaiting.CONFIRM
        );

        state.setActiveIntent(
                Intent.BOOKING_CANCEL
        );

        ChatResponse response =
                payload(
                        "CANCEL:"
                                + pending.id()
                );

        assertTrue(
                response.message()
                        .contains(
                                "đã hủy thao tác"
                        ),
                response.message()
        );

        assertNull(
                state.getPending()
        );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );

        verify(
                bookingService,
                never()
        ).cancel(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Người dùng nói 'đúng rồi' khi chờ xác nhận → thực thi pending")
    void typedConfirmExecutesPending() {

        String json =
                """
                {"branchId":1,"branchName":"Quận 1","branchPhone":"0900000001",
                 "serviceCode":"GYM","facilityId":10,"facilityName":"Máy chạy bộ",
                 "startsAt":"2026-10-04T12:00:00Z","durationMinutes":60}
                """;

        PendingAction pending =
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CREATE,
                        json,
                        TimeUtil.now()
                                .plusSeconds(
                                        300
                                )
                );

        state.setPending(
                pending
        );

        state.setAwaiting(
                Awaiting.CONFIRM
        );

        when(bookingService.create(
                any(),
                any()
        )).thenReturn(
                new BookingResponse(
                        5L,
                        "BOOK_1234567890ABCDEF",
                        1L,
                        1L,
                        1L,
                        ServiceCode.GYM,
                        10L,
                        com.gymfit.booking.BookingStatus.CONFIRMED,
                        TimeUtil.now()
                                .plusSeconds(
                                        86_400
                                ),
                        TimeUtil.now()
                                .plusSeconds(
                                        90_000
                                ),
                        null,
                        null,
                        1L,
                        Instant.now()
                )
        );

        ChatResponse response =
                send(
                        "đúng rồi"
                );

        assertTrue(
                response.message()
                        .contains(
                                "Đã đặt lịch thành công"
                        ),
                response.message()
        );

        assertNull(
                state.getPending()
        );
    }

    @Test
    @DisplayName("Quá hạn mức gửi → trả câu nhắc chờ, không gọi classifier")
    void rateLimited() {

        ReflectionTestUtils.setField(
                manager,
                "rateLimitPerMinute",
                1
        );

        manager.indexHandlers();

        send(
                "xin chào"
        );

        ChatResponse second =
                send(
                        "xin chào again"
                );

        assertTrue(
                second.message()
                        .contains(
                                "hơi nhanh"
                        ),
                second.message()
        );

        assertNull(
                second.sessionId()
        );
    }

    // ------------------------------------------------------------------

    /** Handler giả để kiểm tra dispatch mà không phụ thuộc service. */
    private static final class StubHandler
            implements IntentHandler {

        private final Set<Intent> intents;

        private final String message;

        private StubHandler(
                Set<Intent> intents,
                String message
        ) {
            this.intents =
                    intents;

            this.message =
                    message;
        }

        @Override
        public Set<Intent> supports() {
            return intents;
        }

        @Override
        public ChatResponse handle(
                HandlerContext context
        ) {
            return IntentHandler.message(
                    context,
                    message
            );
        }
    }
}
