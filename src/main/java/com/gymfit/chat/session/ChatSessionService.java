package com.gymfit.chat.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.dialogue.ConversationState;
import com.gymfit.chat.dialogue.PendingAction;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản lý phiên chat: nạp/tạo session, lưu trạng thái, lưu tin nhắn,
 * ghi nhận candidate để huấn luyện.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatTrainingCandidateRepository candidateRepository;

    /**
     * ObjectMapper của Spring — đã có JavaTimeModule nên serialize được
     * {@code Instant}. Dùng {@code new ObjectMapper()} sẽ lỗi với Instant.
     */
    private final ObjectMapper objectMapper;

    @Value("${gymfit.chatbot.session-idle-minutes:30}")
    private int idleMinutes =
            30;

    @Value("${gymfit.chatbot.pending-action-ttl-minutes:5}")
    private int pendingTtlMinutes =
            5;

    @Value("${gymfit.chatbot.history-limit:10}")
    private int historyLimit =
            10;

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    /**
     * Nạp session của user, hoặc tạo mới nếu {@code sessionId} không hợp lệ.
     * <p>Session của user khác → tạo session mới, <b>không</b> tiết lộ.
     */
    @Transactional
    public ChatSession loadOrCreate(
            Long userId,
            String sessionId
    ) {

        Instant now =
                TimeUtil.now();

        if (sessionId != null
                && !sessionId.isBlank()) {

            Optional<ChatSession> found =
                    sessionRepository.findByIdAndUserId(
                            sessionId,
                            userId
                    );

            if (found.isPresent()) {

                ChatSession session =
                        found.get();

                touch(
                        session,
                        now
                );

                return session;
            }
        }

        return create(
                userId,
                now
        );
    }

    @Transactional
    public ChatSession create(
            Long userId,
            Instant now
    ) {
        ChatSession session =
                ChatSession.builder()
                        .id(
                                UUID.randomUUID()
                                        .toString()
                        )
                        .userId(userId)
                        .stateJson(null)
                        .createdAtUtc(now)
                        .updatedAtUtc(now)
                        .build();

        return sessionRepository.save(
                session
        );
    }

    @Transactional(readOnly = true)
    public Optional<ChatSession> find(
            String sessionId,
            Long userId
    ) {
        return sessionRepository.findByIdAndUserId(
                sessionId,
                userId
        );
    }

    private void touch(
            ChatSession session,
            Instant now
    ) {
        session.setUpdatedAtUtc(now);

        sessionRepository.save(
                session
        );
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /**
     * Đọc state từ session; nếu hết hạn thì reset.
     */
    public ConversationState readState(
            ChatSession session
    ) {
        ConversationState state =
                parse(
                        session.getStateJson()
                );

        state.expireIfNeeded(
                TimeUtil.now(),
                idleMinutes,
                pendingTtlMinutes
        );

        return state;
    }

    private ConversationState parse(
            String json
    ) {
        if (json == null
                || json.isBlank()) {
            return new ConversationState(
                    TimeUtil.now()
            );
        }

        try {

            ConversationState state =
                    objectMapper.readValue(
                            json,
                            ConversationState.class
                    );

            if (state == null) {
                return new ConversationState(
                        TimeUtil.now()
                );
            }

            if (state.getSlots() == null) {
                state.setSlots(
                        new java.util.HashMap<>()
                );
            }

            if (state.getLastSlots() == null) {
                state.setLastSlots(
                        new java.util.HashMap<>()
                );
            }

            if (state.getAwaiting() == null) {
                state.setAwaiting(
                        com.gymfit.chat.dialogue.Awaiting.NONE
                );
            }

            return state;

        } catch (Exception exception) {

            log.warn(
                    "Không đọc được state_json, tạo state mới: {}",
                    exception.getMessage()
            );

            return new ConversationState(
                    TimeUtil.now()
            );
        }
    }

    @Transactional
    public ConversationState saveState(
            ChatSession session,
            ConversationState state
    ) {

        state.setUpdatedAt(
                TimeUtil.now()
        );

        try {

            session.setStateJson(
                    objectMapper.writeValueAsString(
                            state
                    )
            );

        } catch (Exception exception) {

            log.warn(
                    "Không ghi được state_json: {}",
                    exception.getMessage()
            );
        }

        session.setUpdatedAtUtc(
                TimeUtil.now()
        );

        sessionRepository.save(
                session
        );

        return state;
    }

    // ------------------------------------------------------------------
    // Message
    // ------------------------------------------------------------------

    @Transactional
    public ChatMessage appendUser(
            String sessionId,
            String text,
            Intent intent,
            Double confidence
    ) {
        return save(
                sessionId,
                ChatMessage.ROLE_USER,
                text,
                intent == null ? null : intent.name(),
                confidence
        );
    }

    @Transactional
    public ChatMessage appendBot(
            String sessionId,
            String text,
            Intent intent,
            Double confidence
    ) {
        return save(
                sessionId,
                ChatMessage.ROLE_BOT,
                text,
                intent == null ? null : intent.name(),
                confidence
        );
    }

    private ChatMessage save(
            String sessionId,
            String role,
            String text,
            String intent,
            Double confidence
    ) {
        ChatMessage message =
                ChatMessage.builder()
                        .sessionId(sessionId)
                        .role(role)
                        .text(
                                PrivacyGuard.mask(
                                        text
                                )
                        )
                        .intent(intent)
                        .confidence(
                                confidence == null
                                        ? null
                                        : BigDecimal.valueOf(confidence)
                                        .setScale(
                                                4,
                                                java.math.RoundingMode.HALF_UP
                                        )
                        )
                        .createdAtUtc(
                                TimeUtil.now()
                        )
                        .build();

        return messageRepository.save(
                message
        );
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> history(
            String sessionId
    ) {
        List<ChatMessage> all =
                messageRepository.findBySessionIdOrderByIdAsc(
                        sessionId
                );

        if (all.size() <= historyLimit) {
            return all;
        }

        return all.subList(
                all.size() - historyLimit,
                all.size()
        );
    }

    /**
     * Ghi nhận đánh giá 👍/👎. Chỉ được đánh giá tin thuộc session của chính user.
     */
    @Transactional
    public Optional<ChatMessage> feedback(
            Long messageId,
            String sessionId,
            Long userId,
            String feedback
    ) {

        Optional<ChatMessage> found =
                messageRepository.findById(
                        messageId
                );

        if (found.isEmpty()) {
            return Optional.empty();
        }

        ChatMessage message =
                found.get();

        if (!sessionId.equals(
                message.getSessionId()
        )) {
            return Optional.empty();
        }

        boolean owns =
                sessionRepository.findByIdAndUserId(
                                sessionId,
                                userId
                        )
                        .isPresent();

        if (!owns) {
            return Optional.empty();
        }

        String value =
                ChatMessage.FEEDBACK_UP.equals(
                        feedback
                )
                        ? ChatMessage.FEEDBACK_UP
                        : ChatMessage.FEEDBACK_DOWN;

        message.setFeedback(
                value
        );

        if (ChatMessage.FEEDBACK_DOWN.equals(
                value
        )) {
            createCandidate(
                    message,
                    ChatTrainingCandidate.STATUS_PENDING
            );
        }

        return Optional.of(
                messageRepository.save(
                        message
                )
        );
    }

    // ------------------------------------------------------------------
    // Training candidate
    // ------------------------------------------------------------------

    @Transactional
    public ChatTrainingCandidate createCandidate(
            ChatMessage message,
            String status
    ) {
        ChatTrainingCandidate candidate =
                ChatTrainingCandidate.builder()
                        .messageId(
                                message == null
                                        ? null
                                        : message.getId()
                        )
                        .text(
                                PrivacyGuard.mask(
                                        message == null
                                                ? ""
                                                : message.getText()
                                )
                        )
                        .predictedIntent(
                                message == null
                                        || message.getIntent() == null
                                        ? "OUT_OF_SCOPE"
                                        : message.getIntent()
                        )
                        .confidence(
                                message == null
                                        || message.getConfidence() == null
                                        ? BigDecimal.ZERO
                                        : message.getConfidence()
                        )
                        .status(status)
                        .createdAtUtc(
                                TimeUtil.now()
                        )
                        .build();

        return candidateRepository.save(
                candidate
        );
    }

    @Transactional
    public ChatTrainingCandidate createCandidate(
            String text,
            String predictedIntent,
            double confidence
    ) {
        ChatTrainingCandidate candidate =
                ChatTrainingCandidate.builder()
                        .messageId(null)
                        .text(
                                PrivacyGuard.mask(
                                        text
                                )
                        )
                        .predictedIntent(
                                predictedIntent == null
                                        ? "OUT_OF_SCOPE"
                                        : predictedIntent
                        )
                        .confidence(
                                BigDecimal.valueOf(confidence)
                                        .setScale(
                                                4,
                                                java.math.RoundingMode.HALF_UP
                                        )
                        )
                        .status(
                                ChatTrainingCandidate.STATUS_PENDING
                        )
                        .createdAtUtc(
                                TimeUtil.now()
                        )
                        .build();

        return candidateRepository.save(
                candidate
        );
    }

    public int idleMinutes() {
        return idleMinutes;
    }

    public int pendingTtlMinutes() {
        return pendingTtlMinutes;
    }

    public int historyLimit() {
        return historyLimit;
    }

}