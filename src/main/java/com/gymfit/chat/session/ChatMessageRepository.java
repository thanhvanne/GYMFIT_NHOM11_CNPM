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
}