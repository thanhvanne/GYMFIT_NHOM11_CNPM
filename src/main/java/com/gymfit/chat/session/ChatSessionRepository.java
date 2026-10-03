package com.gymfit.chat.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository
        extends JpaRepository<ChatSession, String> {

    /** Chỉ trả về session thuộc đúng user đó — không để lộ session của người khác. */
    Optional<ChatSession> findByIdAndUserId(
            String id,
            Long userId
    );

    List<ChatSession> findTop20ByUserIdOrderByUpdatedAtUtcDesc(
            Long userId
    );
}