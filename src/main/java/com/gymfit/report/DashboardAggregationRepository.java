package com.gymfit.report;

import com.gymfit.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface DashboardAggregationRepository
        extends JpaRepository<Member, Long> {

    @Query(value = """
        SELECT COALESCE(SUM(total), 0)
        FROM sales_order
        WHERE status = 'PAID'
          AND paid_at_utc >= :fromUtc
          AND paid_at_utc < :toUtc
          AND (:branchId IS NULL OR branch_id = :branchId)
        """, nativeQuery = true)
    BigDecimal revenue(
            @Param("branchId") Long branchId,
            @Param("fromUtc") Instant fromUtc,
            @Param("toUtc") Instant toUtc
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM member
        WHERE status = 'ACTIVE'
          AND (:branchId IS NULL OR home_branch_id = :branchId)
        """, nativeQuery = true)
    long activeMembers(@Param("branchId") Long branchId);

    @Query(value = """
        SELECT COUNT(*)
        FROM booking
        WHERE starts_at_utc >= :fromUtc
          AND starts_at_utc < :toUtc
          AND status <> 'CANCELLED'
          AND (:branchId IS NULL OR branch_id = :branchId)
        """, nativeQuery = true)
    long bookings(
            @Param("branchId") Long branchId,
            @Param("fromUtc") Instant fromUtc,
            @Param("toUtc") Instant toUtc
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM check_in
        WHERE result = 'ACCEPTED'
          AND created_at_utc >= :fromUtc
          AND created_at_utc < :toUtc
          AND (:branchId IS NULL OR branch_id = :branchId)
        """, nativeQuery = true)
    long checkIns(
            @Param("branchId") Long branchId,
            @Param("fromUtc") Instant fromUtc,
            @Param("toUtc") Instant toUtc
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM membership
        WHERE status = 'ACTIVE'
          AND start_date <= :today
          AND end_date >= :today
          AND (:branchId IS NULL OR branch_id = :branchId)
        """, nativeQuery = true)
    long activeMemberships(
            @Param("branchId") Long branchId,
            @Param("today") java.time.LocalDate today
    );
}