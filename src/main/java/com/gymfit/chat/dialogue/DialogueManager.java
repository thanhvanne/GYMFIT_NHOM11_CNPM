package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.dialogue.handler.FaqHandler;
import com.gymfit.chat.dialogue.handler.HandlerContext;
import com.gymfit.chat.dialogue.handler.IntentHandler;
import com.gymfit.chat.dto.ChatRequest;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.knowledge.FaqRetriever;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.chat.nlu.ChatPipeline;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlu.IntentClassifier;
import com.gymfit.chat.nlu.IntentPrediction;
import com.gymfit.chat.nlu.NormalizedText;
import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.chat.session.ChatMessage;
import com.gymfit.chat.session.ChatSession;
import com.gymfit.chat.session.ChatSessionService;
import com.gymfit.chat.session.RateLimiter;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.user.RoleCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bộ điều phối hội thoại: đọc trạng thái → phân loại → kiểm quyền → dispatch
 * handler → lưu phiên.
 *
 * <p>Thứ tự quyết định (không đảo được):
 * <ol>
 *     <li>Giới hạn tần suất gửi</li>
 *     <li>Trả lời payload {@code CONFIRM}/{@code CANCEL} (nút bấm)</li>
 *     <li>Mâu thuẫn với câu đang chờ xác nhận (đúng/sai)</li>
 *     <li>Điền slot cho câu hỏi đang chờ</li>
 *     <li>Ngưỡng tin cậy: chấp nhận / làm rõ / fallback</li>
 *     <li>Ma trận quyền intent × vai trò</li>
 *     <li>Dispatch handler</li>
 * </ol>
 *
 * <p>Không phần nào ném lỗi ra HTTP: mọi lỗi đều thành câu tiếng Việt.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DialogueManager {

    private final ChatPipeline pipeline;

    private final IntentClassifier classifier;

    private final IntentPolicy policy;

    private final List<IntentHandler> handlerBeans;

    private final ChatSessionService sessions;

    private final RateLimiter rateLimiter;

    private final ResponseTemplates templates;

    private final com.gymfit.chat.knowledge.FaqRetriever faqRetriever;

    private final BookingActionExecutor executor;

    private final ObjectMapper objectMapper;

    /** intent → handler (xây lúc khởi động, phát hiện trùng lặp ngay). */
    private Map<Intent, IntentHandler> handlers =
            Map.of();

    @Value("${gymfit.chatbot.threshold-accept:0.70}")
    private double thresholdAccept =
            0.70;

    @Value("${gymfit.chatbot.threshold-clarify:0.40}")
    private double thresholdClarify =
            0.40;

    @Value("${gymfit.chatbot.rate-limit-per-minute:20}")
    private int rateLimitPerMinute =
            20;

    @PostConstruct
    void indexHandlers() {

        Map<Intent, IntentHandler> indexed =
                new EnumMap<>(Intent.class);

        for (IntentHandler handler : handlerBeans) {

            for (Intent intent : handler.supports()) {

                IntentHandler previous =
                        indexed.put(
                                intent,
                                handler
                        );

                if (previous != null) {

                    log.error(
                            "Intent {} được xử lý bởi {} và {}",
                            intent,
                            previous.getClass()
                                    .getSimpleName(),
                            handler.getClass()
                                    .getSimpleName()
                    );

                    throw new IllegalStateException(
                            "Trùng handler cho intent "
                                    + intent
                    );
                }
            }
        }

        handlers =
                Map.copyOf(indexed);

        rateLimiter.setLimit(
                rateLimitPerMinute
        );

        log.info(
                "Chatbot: {} intent có handler / {} intent, ngưỡng accept={} clarify={}",
                handlers.size(),
                Intent.values().length,
                thresholdAccept,
                thresholdClarify
        );
    }

    // ------------------------------------------------------------------
    // Vòng đời một lượt chat
    // ------------------------------------------------------------------

    public ChatResponse handle(
            AppPrincipal principal,
            ChatRequest request
    ) {

        if (principal == null
                || principal.getUserId() == null) {

            return plain(
                    templates.get(
                            "error.generic"
                    )
            );
        }

        Instant now =
                TimeUtil.now();

        Long userId =
                principal.getUserId();

        if (!rateLimiter.tryAcquire(
                userId,
                now
        )) {

            return plain(
                    templates.get(
                            "error.rate",
                            Map.of(
                                    "seconds",
                                    rateLimiter.retryAfterSeconds(
                                            userId,
                                            now
                                    )
                            )
                    )
            );
        }

        ChatRequest actual =
                request == null
                        ? new ChatRequest(
                        null,
                        null,
                        null
                )
                : request;

        String payload =
                actual.trimmedPayload();

        String message =
                actual.trimmedMessage();

        // Nút bấm gửi sẵn "TEXT:<nội dung>" → coi như người dùng gõ
        if (payload != null
                && payload.startsWith(
                "TEXT:"
        )) {

            message =
                    payload.substring(
                            5
                    )
                            .trim();

            payload =
                    null;
        }

        if (payload != null
                && !isConfirmPayload(
                payload
        )) {

            // Payload lạ → coi là câu nói
            message =
                    payload;

            payload =
                    null;
        }

        if (isBlank(
                message
        ) && payload == null) {

            return plain(
                    templates.get(
                            "error.empty"
                    )
            );
        }

        try {

            ChatSession session =
                    sessions.loadOrCreate(
                            userId,
                            actual.trimmedSessionId()
                    );

            ConversationState state =
                    sessions.readState(
                            session
                    );

            Draft draft;

            if (payload != null) {

                draft = payloadTurn(
                        principal,
                        state,
                        payload,
                        session
                );

            } else {

                draft = messageTurn(
                        principal,
                        session,
                        state,
                        message
                );
            }

            sessions.saveState(
                    session,
                    state
            );

            return finish(
                    session,
                    draft
            );

        } catch (ApiException exception) {

            log.warn(
                    "Chat lỗi {}: {}",
                    exception.getCode(),
                    exception.getMessage()
            );

            return plain(
                    templates.fromException(
                            exception
                    )
            );

        } catch (Exception exception) {

            log.error(
                    "Chat lỗi không mong đợi: {}",
                    exception.getMessage(),
                    exception
            );

            return plain(
                    templates.get(
                            "error.generic"
                    )
            );
        }
    }

    // ------------------------------------------------------------------
    // Lượt bấm nút Xác nhận / Hủy
    // ------------------------------------------------------------------

    private Draft payloadTurn(
            AppPrincipal principal,
            ConversationState state,
            String payload,
            ChatSession session
    ) {

        sessions.appendUser(
                session.getId(),
                payload,
                null,
                null
        );

        PendingAction pending =
                state.getPending();

        if (payload.startsWith(
                "CONFIRM:"
        )) {

            String id =
                    payload.substring(
                            8
                    )
                            .trim();

            if (pending == null
                    || pending.isExpired(
                    TimeUtil.now()
            )) {

                clearConfirm(
                        state
                );

                return reply(
                        Intent.CONFIRM_YES,
                        1.0,
                        templates.get(
                                "pending.expired"
                        )
                );
            }

            if (!pending.matches(
                    id
            )) {

                return reply(
                        Intent.CONFIRM_YES,
                        1.0,
                        templates.get(
                                "pending.invalid"
                        )
                );
            }

            // Dọn trước khi chạy: không bao giờ lặp lại thao tác ghi dữ liệu
            clearConfirm(
                    state
            );

            ChatResponse executed =
                    executor.execute(
                            principal,
                            pending
                    );

            return reply(
                    actionIntent(
                            pending
                    ),
                    1.0,
                    executed.message(),
                    executed.card(),
                    executed.suggestions()
            );
        }

        // CANCEL:<uuid>
        if (pending == null) {

            return reply(
                    Intent.CONFIRM_NO,
                    1.0,
                    templates.get(
                            "pending.invalid"
                    )
            );
        }

        clearConfirm(
                state
        );

        return reply(
                Intent.CONFIRM_NO,
                1.0,
                templates.get(
                        "pending.cancelled"
                )
        );
    }

    // ------------------------------------------------------------------
    // Lượt nhắn tin
    // ------------------------------------------------------------------

    private Draft messageTurn(
            AppPrincipal principal,
            ChatSession session,
            ConversationState state,
            String message
    ) {

        LocalDate today =
                TimeUtil.today();

        NormalizedText text =
                pipeline.normalize(
                        message
                );

        Entities entities =
                pipeline.extract(
                        text,
                        today
                );

        IntentPrediction prediction =
                classifier.classify(
                        text,
                        entities,
                        today
                );

        Intent intent =
                policy.remap(
                        principal.getRole(),
                        prediction.intent()
                );

        double confidence =
                prediction.confidence();

        sessions.appendUser(
                session.getId(),
                message,
                intent,
                confidence
        );

        // ---- 1. Câu đang chờ xác nhận ----
        if (state.getAwaiting()
                == Awaiting.CONFIRM) {

            if (intent == Intent.CONFIRM_YES) {

                PendingAction pending =
                        state.getPending();

                if (pending != null) {

                    clearConfirm(
                            state
                    );

                    ChatResponse executed =
                            executor.execute(
                                    principal,
                                    pending
                            );

                    return reply(
                            actionIntent(
                                    pending
                            ),
                            1.0,
                            executed.message(),
                            executed.card(),
                            executed.suggestions()
                    );
                }

                Intent target =
                        state.getActiveIntent();

                if (target == null) {

                    clearConfirm(
                            state
                    );

                    return reply(
                            Intent.CONFIRM_YES,
                            confidence,
                            templates.get(
                                    "pending.invalid"
                            )
                    );
                }

                // Chấp nhận câu làm rõ → xử lý lại intent đã làm rõ
                state.setAwaiting(
                        Awaiting.NONE
                );

                state.setActiveIntent(
                        null
                );

                intent =
                        target;
            }

            else if (intent == Intent.CONFIRM_NO) {

                boolean clarify =
                        state.getPending() == null
                                && state.getActiveIntent()
                                != null;

                clearConfirm(
                        state
                );

                return reply(
                        Intent.CONFIRM_NO,
                        confidence,
                        templates.get(
                                clarify
                                        ? "clarify.no"
                                        : "pending.cancelled"
                        )
                );
            }

            else {
                // Người dùng chuyển đề tài → bỏ qua thao tác đang chờ
                clearConfirm(
                        state
                );
            }
        }

        // ---- 2. Điền slot cho câu hỏi đang chờ ----
        intent = continueOrSwitch(
                state,
                intent,
                entities
        );

        // ---- 3. Ngưỡng tin cậy ----
        Draft gated =
                gate(
                        intent,
                        confidence,
                        prediction,
                        state,
                        principal.getRole(),
                        message
                );

        if (gated != null) {
            return gated;
        }

        // ---- 4. Quyền ----
        if (!policy.isAllowed(
                principal.getRole(),
                intent
        )) {

            return reply(
                    intent,
                    confidence,
                    templates.get(
                            "denied.role",
                            Map.of(
                                    "allowed",
                                    allowedLabel(
                                            principal.getRole()
                                    )
                            )
                    ),
                    null,
                    quickSuggestions(
                            principal.getRole()
                    )
            );
        }

        IntentHandler handler =
                handlers.get(
                        intent
                );

        if (handler == null) {

            return fallback(
                    intent,
                    confidence,
                    principal.getRole()
            );
        }

        HandlerContext context =
                new HandlerContext(
                        principal,
                        intent,
                        entities,
                        text,
                        message,
                        today,
                        state.getSlots(),
                        state
                );

        ChatResponse response =
                handler.handle(
                        context
                );

        state.remember(
                intent
        );

        if (state.getAwaiting()
                == Awaiting.NONE) {
            state.setActiveIntent(
                    null
            );
        }

        return reply(
                intent,
                confidence,
                response.message(),
                response.card(),
                response.suggestions()
        );
    }

    /**
     * Bot đang chờ slot: câu trả lời có entity thì điền vào đúng intent đang
     * chờ; câu trả lời không có entity thì để mô hình phân loại như bình thường.
     */
    private Intent continueOrSwitch(
            ConversationState state,
            Intent intent,
            Entities entities
    ) {

        Awaiting awaiting =
                state.getAwaiting();

        if (awaiting == Awaiting.NONE
                || awaiting == Awaiting.CONFIRM) {
            return intent;
        }

        boolean hasEntity =
                entities != null
                        && entities.hasEntity();

        Intent active =
                state.getActiveIntent();

        if (awaiting == Awaiting.BOOKING_PICK) {

            if (entities != null
                    && entities.bookingCode()
                    != null) {
                return Intent.BOOKING_CANCEL;
            }

            return intent;
        }

        if (hasEntity
                && active != null) {

            return active;
        }

        return intent;
    }

    /**
     * Ngưỡng: trên {@code threshold-accept} → chạy; giữa hai ngưỡng → làm
     * rõ; dưới {@code threshold-clarify} → fallback + ghi candidate huấn luyện.
     *
     * @return câu trả lời nếu không được phép chạy, {@code null} nếu chạy tiếp
     */
    private Draft gate(
            Intent intent,
            double confidence,
            IntentPrediction prediction,
            ConversationState state,
            RoleCode role,
            String message
    ) {

        // Rules guard đã quyết định → độ tin cậy 1.0, không cần ngưỡng
        if (prediction.fromRule()) {
            return null;
        }

        boolean outOfScope =
                intent == Intent.OUT_OF_SCOPE
                        || intent == null;

        if (outOfScope
                || confidence < thresholdClarify) {

            Draft rescued =
                    faqRescue(
                            message,
                            role
                    );

            if (rescued != null) {
                return rescued;
            }

            recordCandidate(
                    message,
                    intent,
                    confidence,
                    confidence < thresholdClarify
            );

            return fallback(
                    intent,
                    confidence,
                    role
            );
        }

        if (confidence < thresholdAccept) {

            Draft rescued =
                    faqRescue(
                            message,
                            role
                    );

            if (rescued != null) {
                return rescued;
            }

            state.setAwaiting(
                    Awaiting.CONFIRM
            );

            state.setActiveIntent(
                    intent
            );

            state.setPending(
                    null
            );

            return reply(
                    intent,
                    confidence,
                    templates.get(
                            "clarify",
                            Map.of(
                                    "intent_desc",
                                    policy.describe(
                                            intent
                                    )
                            )
                    ),
                    null,
                    List.of(
                            ChatSuggestion.of(
                                    "Đúng rồi"
                            ),
                            ChatSuggestion.of(
                                    "Không"
                            )
                    )
            );
        }

        return null;
    }

    /**
     * Chuỗi cứu hộ FAQ (plan 5.3): trước khi rơi vào làm rõ/fallback, thử truy
     * vấn {@link FaqRetriever} bằng chính câu người dùng.
     *
     * @return {@code null} nếu kho không có mục đủ điểm — trả về {@code null}
     * để {@link #gate} chạy tiếp hành vi cũ
     */
    private Draft faqRescue(
            String message,
            RoleCode role
    ) {

        if (message == null
                || message.isBlank()) {
            return null;
        }

        FaqRetriever.Result result =
                faqRetriever.retrieve(
                        message,
                        role
                );

        // Chỉ cứu khi TRÚNG chắc (điểm ≥ THRESHOLD_ANSWER). Ở mức gợi ý
        // [THRESHOLD_SUGGEST, THRESHOLD_ANSWER) mà cũng ưu tiên thì bot sẽ
        // đưa chip "Có phải bạn muốn hỏi:" thay cho luồng làm rõ/fallback -
        // ví dụ "mai tôi có lịch không" bị chặn trước khi xem lịch.
        if (result == null
                || !result.hit()) {
            return null;
        }

        FaqHandler.Reply reply =
                FaqHandler.render(
                        templates,
                        result
                );

        return reply(
                Intent.FAQ_GENERAL,
                result.score(),
                reply.message(),
                reply.card(),
                reply.suggestions()
        );
    }

    // ------------------------------------------------------------------
    // Câu trả lời khung
    // ------------------------------------------------------------------

    private Draft fallback(
            Intent intent,
            double confidence,
            RoleCode role
    ) {
        return reply(
                intent == null
                        ? Intent.OUT_OF_SCOPE
                        : intent,
                confidence,
                templates.first(
                        "fallback",
                        Map.of()
                ),
                null,
                quickSuggestions(
                        role
                )
        );
    }

    private List<ChatSuggestion> quickSuggestions(
            RoleCode role
    ) {
        return policy.suggestionsFor(
                        role
                )
                .stream()
                .map(
                        ChatSuggestion::of
                )
                .toList();
    }

    private String allowedLabel(
            RoleCode role
    ) {

        String allowed =
                policy.allowedIntents(
                                role
                        )
                        .keySet()
                        .stream()
                        .limit(6)
                        .map(
                                policy::describe
                        )
                        .collect(
                                Collectors.joining(
                                        ", "
                                )
                        );

        return allowed.isBlank()
                ? "hỏi về gói tập và thông tin chi nhánh"
                : allowed;
    }

    private void clearConfirm(
            ConversationState state
    ) {
        state.setPending(
                null
        );

        state.setAwaiting(
                Awaiting.NONE
        );

        state.setActiveIntent(
                null
        );
    }

    private void recordCandidate(
            String message,
            Intent intent,
            double confidence,
            boolean lowConfidence
    ) {

        if (!lowConfidence) {
            return;
        }

        try {

            sessions.createCandidate(
                    message,
                    intent == null
                            ? "OUT_OF_SCOPE"
                            : intent.name(),
                    confidence
            );

        } catch (Exception exception) {

            log.warn(
                    "Không ghi được training candidate: {}",
                    exception.getMessage()
            );
        }
    }

    private static Intent actionIntent(
            PendingAction pending
    ) {
        return PendingAction.TYPE_BOOKING_CANCEL
                .equals(
                        pending.type()
                )
                ? Intent.BOOKING_CANCEL
                : Intent.BOOKING_CREATE;
    }

    private static boolean isConfirmPayload(
            String payload
    ) {
        return payload.startsWith(
                "CONFIRM:"
        ) || payload.startsWith(
                "CANCEL:"
        );
    }

    private static boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }

    private static ChatResponse plain(
            String message
    ) {
        return ChatResponse.of(
                null,
                message,
                null,
                null
        );
    }

    private static Draft reply(
            Intent intent,
            double confidence,
            String message
    ) {
        return reply(
                intent,
                confidence,
                message,
                null,
                null
        );
    }

    private static Draft reply(
            Intent intent,
            double confidence,
            String message,
            com.gymfit.chat.dto.ChatCard card,
            List<ChatSuggestion> suggestions
    ) {

        ChatResponse response =
                ChatResponse.of(
                        null,
                        message,
                        intent == null
                                ? null
                                : intent.name(),
                        confidence
                );

        if (card != null) {
            response =
                    response.withCard(
                            card
                    );
        }

        if (suggestions != null) {
            response =
                    response.withSuggestions(
                            suggestions
                    );
        }

        return new Draft(
                response,
                intent,
                confidence
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse finish(
            ChatSession session,
            Draft draft
    ) {

        ChatMessage bot =
                sessions.appendBot(
                        session.getId(),
                        draft.response()
                                .message(),
                        draft.intent(),
                        draft.confidence()
                );

        return new ChatResponse(
                session.getId(),
                draft.response()
                        .message(),
                draft.response()
                        .intent(),
                draft.response()
                        .confidence(),
                draft.response()
                        .suggestions(),
                draft.response()
                        .card(),
                bot.getId(),
                draft.response()
                        .createdAtUtc()
        );
    }

    /**
     * Kết quả một lượt: câu trả lời + intent/confidence để ghi lịch sử.
     */
    private record Draft(
            ChatResponse response,
            Intent intent,
            double confidence
    ) {
    }
}
