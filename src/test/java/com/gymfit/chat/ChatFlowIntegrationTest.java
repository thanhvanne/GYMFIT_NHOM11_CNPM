package com.gymfit.chat;

import com.gymfit.chat.dto.ChatRequest;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.FeedbackRequest;
import com.gymfit.chat.session.ChatMessage;
import com.gymfit.chat.session.ChatMessageRepository;
import com.gymfit.chat.session.ChatSession;
import com.gymfit.chat.session.ChatSessionRepository;
import com.gymfit.chat.session.ChatTrainingCandidate;
import com.gymfit.chat.session.ChatTrainingCandidateRepository;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * luồng chat thật với DB: phiên → tin nhắn → phản hồi 👎 → candidate huấn luyện.
 */
@SpringBootTest
class ChatFlowIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatTrainingCandidateRepository candidateRepository;

    @Autowired
    private AppUserRepository userRepository;

    private AppPrincipal principal;

    private Instant startedAt;

    @BeforeEach
    void setUp() {

        startedAt =
                Instant.now()
                        .minusSeconds(
                                1
                        );

        principal =
                new AppPrincipal(
                        newUser(
                                "Hội viên chatbot"
                        )
                );
    }

    private AppUser newUser(
            String fullName
    ) {

        Instant now =
                Instant.now();

        return userRepository.save(
                AppUser.builder()
                        .fullName(
                                fullName
                        )
                        .email(
                                "chatbot.it."
                                + System.nanoTime()
                                + "@gymfit.local"
                        )
                        .passwordHash(
                                "x"
                        )
                        .roleCode(
                                RoleCode.ADMIN
                        )
                        .status(
                                UserStatus.ACTIVE
                        )
                        .createdAtUtc(
                                now
                        )
                        .updatedAtUtc(
                                now
                        )
                        .build()
        );
    }

    @AfterEach
    void cleanUp() {

        Long userId =
                principal == null
                        ? null
                        : principal.getUserId();

        if (userId == null) {
            return;
        }

        for (ChatSession session :
                sessionRepository.findTop20ByUserIdOrderByUpdatedAtUtcDesc(
                        userId
                )) {

            for (ChatMessage message :
                    messageRepository.findBySessionIdOrderByIdAsc(
                            session.getId()
                    )) {

                messageRepository.delete(
                        message
                );
            }

            sessionRepository.delete(
                    session
            );
        }

        for (ChatTrainingCandidate candidate :
                candidateRepository.findByStatusAndCreatedAtUtcAfterOrderByCreatedAtUtcDesc(
                        ChatTrainingCandidate.STATUS_PENDING,
                        startedAt
                )) {

            candidateRepository.delete(
                    candidate
            );
        }

        userRepository.findById(
                        userId
                )
                .ifPresent(
                        userRepository::delete
                );
    }

    // ==================================================================

    @Test
    @DisplayName("Một lượt chat → lưu phiên + 2 tin nhắn (user, bot) vào DB")
    void turnPersistsSessionAndMessages() {

        ChatResponse response =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                "xin chào",
                                null,
                                null
                        )
                );

        assertNotNull(
                response.sessionId()
        );

        assertTrue(
                !response.message()
                        .isBlank(),
                "Không được trả lời rỗng: "
                        + response.message()
        );

        assertNotNull(
                response.messageId()
        );

        ChatSession session =
                sessionRepository.findByIdAndUserId(
                                response.sessionId(),
                                principal.getUserId()
                        )
                        .orElseThrow();

        List<ChatMessage> messages =
                messageRepository.findBySessionIdOrderByIdAsc(
                        session.getId()
                );

        assertEquals(
                2,
                messages.size()
        );

        assertEquals(
                ChatMessage.ROLE_USER,
                messages.get(0)
                        .getRole()
        );

        assertEquals(
                "xin chào",
                messages.get(0)
                        .getText()
        );

        assertEquals(
                ChatMessage.ROLE_BOT,
                messages.get(1)
                        .getRole()
        );

        assertEquals(
                "GREETING",
                messages.get(1)
                        .getIntent()
        );

        assertNotNull(
                messages.get(1)
                        .getConfidence()
        );
    }

    @Test
    @DisplayName("Gửi kèm sessionId → dùng lại phiên cũ, không tạo phiên mới")
    void sessionIdIsReused() {

        ChatResponse first =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                "xin chào",
                                null,
                                null
                        )
                );

        long before =
                sessionRepository.findTop20ByUserIdOrderByUpdatedAtUtcDesc(
                                principal.getUserId()
                )
                .size();

        ChatResponse second =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                "tạm biệt",
                                first.sessionId(),
                                null
                        )
                );

        assertEquals(
                first.sessionId(),
                second.sessionId()
        );

        assertEquals(
                before,
                sessionRepository.findTop20ByUserIdOrderByUpdatedAtUtcDesc(
                                principal.getUserId()
                )
                .size()
        );

        assertEquals(
                4,
                messageRepository.findBySessionIdOrderByIdAsc(
                                first.sessionId()
                )
                .size()
        );
    }

    @Test
    @DisplayName("SessionId của người khác → tạo phiên mới, không đọc được phiên đó")
    void sessionIdOfOtherUserIsIgnored() {

        ChatResponse mine =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                "xin chào",
                                null,
                                null
                        )
                );

        AppUser other =
                newUser(
                        "Người khác"
                );

        AppPrincipal otherPrincipal =
                new AppPrincipal(
                        other
                );

        ChatResponse theirs =
                chatService.chat(
                        otherPrincipal,
                        new ChatRequest(
                                "xin chào",
                                mine.sessionId(),
                                null
                        )
                );

        assertFalse(
                mine.sessionId()
                        .equals(
                                theirs.sessionId()
                        ),
                "Không được dùng chung phiên giữa hai người"
        );

        // Dọn session + tài khoản phụ
        sessionRepository.findByIdAndUserId(
                        theirs.sessionId(),
                        otherPrincipal.getUserId()
        )
                .ifPresent(session -> {

                    for (ChatMessage message :
                            messageRepository.findBySessionIdOrderByIdAsc(
                                    session.getId()
                            )) {

                        messageRepository.delete(
                                message
                        );
                    }

                    sessionRepository.delete(
                            session
                    );
                });

        userRepository.delete(
                other
        );
    }

    @Test
    @DisplayName("Đánh giá 👎 → ghi feedback và tạo candidate để huấn luyện lại")
    void feedbackDownCreatesCandidate() {

        ChatResponse response =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                "xin chào",
                                null,
                                null
                        )
                );

        long before =
                candidateRepository.countByStatus(
                        ChatTrainingCandidate.STATUS_PENDING
                );

        ChatResponse feedback =
                chatService.feedback(
                        principal,
                        new FeedbackRequest(
                                response.sessionId(),
                                response.messageId(),
                                "DOWN"
                        )
                );

        assertTrue(
                feedback.message()
                        .contains(
                                "Cảm ơn"
                        )
        );

        assertEquals(
                before + 1,
                candidateRepository.countByStatus(
                        ChatTrainingCandidate.STATUS_PENDING
                )
        );

        ChatMessage bot =
                messageRepository.findById(
                                response.messageId()
                        )
                        .orElseThrow();

        assertEquals(
                ChatMessage.FEEDBACK_DOWN,
                bot.getFeedback()
        );
    }

    @Test
    @DisplayName("Đánh giá tin không thuộc phiên mình → bỏ qua, không ghi")
    void feedbackOfForeignSessionIgnored() {

        ChatResponse response =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                "xin chào",
                                null,
                                null
                        )
                );

        long before =
                candidateRepository.countByStatus(
                        ChatTrainingCandidate.STATUS_PENDING
                );

        chatService.feedback(
                principal,
                new FeedbackRequest(
                        "session-khong-phai-cua-toi",
                        response.messageId(),
                        "DOWN"
                )
        );

        ChatMessage bot =
                messageRepository.findById(
                                response.messageId()
                        )
                        .orElseThrow();

        assertNull(
                bot.getFeedback()
        );

        assertEquals(
                before,
                candidateRepository.countByStatus(
                        ChatTrainingCandidate.STATUS_PENDING
                )
        );
    }

    @Test
    @DisplayName("Bấm Xác nhận khi không có gì chờ → câu 'hết hạn', không lỗi HTTP")
    void confirmWithoutPendingIsHandled() {

        ChatResponse response =
                chatService.chat(
                        principal,
                        new ChatRequest(
                                null,
                                null,
                                "CONFIRM:khong-ton-tai"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "hết hạn"
                        ),
                response.message()
        );

        assertNotNull(
                response.sessionId()
        );
    }
}
