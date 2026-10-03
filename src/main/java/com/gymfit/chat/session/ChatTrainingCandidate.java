package com.gymfit.chat.session;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Câu hỏi mà bot không trả lời chắc chắn (confidence thấp) hoặc người dùng
 * đánh giá thấp → dùng để admin gán nhãn lại và bổ sung vào tập huấn luyện.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_training_candidate")
public class ChatTrainingCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    @Column(name = "predicted_intent", nullable = false, length = 40)
    private String predictedIntent;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "label", length = 40)
    private String label;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;

    @Column(name = "labeled_by_user_id")
    private Long labeledByUserId;

    @Column(name = "labeled_at_utc")
    private Instant labeledAtUtc;

    public static final String STATUS_PENDING =
            "PENDING";

    public static final String STATUS_LABELED =
            "LABELED";

    public static final String STATUS_REJECTED =
            "REJECTED";
}