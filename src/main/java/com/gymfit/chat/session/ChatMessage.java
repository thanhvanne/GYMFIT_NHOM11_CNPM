package com.gymfit.chat.session;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một tin nhắn trong phiên chat ({@code USER} hoặc {@code BOT}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    @Column(name = "intent", length = 40)
    private String intent;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "feedback", length = 10)
    private String feedback;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;

    public static final String ROLE_USER =
            "USER";

    public static final String ROLE_BOT =
            "BOT";

    public static final String FEEDBACK_UP =
            "UP";

    public static final String FEEDBACK_DOWN =
            "DOWN";
}