package com.gymfit.booking;

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
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 40, unique = true)
    private String bookingCode;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "membership_id", nullable = false)
    private Long membershipId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false, length = 20)
    private ServiceCode serviceCode;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "starts_at_utc", nullable = false)
    private Instant startsAtUtc;

    @Column(name = "ends_at_utc", nullable = false)
    private Instant endsAtUtc;

    @Column(name = "cancelled_at_utc")
    private Instant cancelledAtUtc;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}