package com.gymfit.checkin;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "qr_token_use")
public class QrTokenUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_jti", nullable = false, length = 80, unique = true)
    private String tokenJti;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "used_at_utc", nullable = false)
    private Instant usedAtUtc;
}