package com.gymfit.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    List<Booking> findAllByMemberIdOrderByStartsAtUtcDesc(
            Long memberId
    );

    List<Booking> findAllByBranchIdOrderByStartsAtUtcDesc(
            Long branchId
    );

    List<Booking> findAllByOrderByStartsAtUtcDesc();

    @Query("""
        select count(b)
        from Booking b
        where b.memberId = :memberId
        and b.status = com.gymfit.booking.BookingStatus.CONFIRMED
        and b.startsAtUtc < :endsAt
        and b.endsAtUtc > :startsAt
        """)
    long countMemberOverlap(
            Long memberId,
            Instant startsAt,
            Instant endsAt
    );

    @Query("""
        select count(b)
        from Booking b
        where b.facilityId = :facilityId
        and b.status = com.gymfit.booking.BookingStatus.CONFIRMED
        and b.startsAtUtc < :endsAt
        and b.endsAtUtc > :startsAt
        """)
    long countFacilityOverlap(
            Long facilityId,
            Instant startsAt,
            Instant endsAt
    );
}