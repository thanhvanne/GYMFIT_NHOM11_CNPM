package com.gymfit.chat.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop30BySessionIdOrderByIdDesc(
            String sessionId
    );

    List<ChatMessage> findBySessionIdOrderByIdAsc(
            String sessionId
    );

    /** Tin của một vai trò sau mốc thời gian – dùng cho thống kê 24 giờ. */
    List<ChatMessage> findByRoleAndCreatedAtUtcAfter(
            String role,
            java.time.Instant after
    );

    /** Các câu trả lời đã được 👍/👎 – dùng cho thống kê phản hồi. */
    List<ChatMessage> findTop500ByFeedbackNotNullOrderByIdDesc();
}