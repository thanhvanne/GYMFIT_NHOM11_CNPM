package com.gymfit.chat.session;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Phiên chat của một người dùng.
 * <p>{@code id} là UUID dạng chuỗi (client giữ, mỗi lượt gửi kèm).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "state_json", columnDefinition = "NVARCHAR(MAX)")
    private String stateJson;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;
}