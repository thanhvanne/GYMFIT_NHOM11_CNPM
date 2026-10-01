package com.gymfit.checkin;

import com.gymfit.branch.ServiceCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "check_in")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "membership_id")
    private Long membershipId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false, length = 20)
    private ServiceCode serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInResult result;

    @Column(length = 60)
    private String reason;

    @Column(name = "performed_by_user_id", nullable = false)
    private Long performedByUserId;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}