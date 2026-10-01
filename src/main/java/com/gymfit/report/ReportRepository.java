package com.gymfit.report;

import java.util.List;
import com.gymfit.order.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ReportRepository
        extends JpaRepository<SalesOrder, Long> {

    @Query(value = """
    WITH services AS (
        SELECT 'GYM' AS service_code
        UNION ALL
        SELECT 'BOXING'
        UNION ALL
        SELECT 'PICKLEBALL'
    )
    SELECT
        s.service_code AS serviceCode,

        (
            SELECT COUNT(*)
            FROM booking b
            WHERE b.service_code = s.service_code
              AND b.starts_at_utc >= :fromUtc
              AND b.starts_at_utc < :toUtc
              AND b.status <> 'CANCELLED'
              AND (:branchId IS NULL OR b.branch_id = :branchId)
        ) AS bookings,

        (
            SELECT COUNT(*)
            FROM check_in c
            WHERE c.service_code = s.service_code
              AND c.created_at_utc >= :fromUtc
              AND c.created_at_utc < :toUtc
              AND c.result = 'ACCEPTED'
              AND (:branchId IS NULL OR c.branch_id = :branchId)
        ) AS checkIns

    FROM services s
    ORDER BY s.service_code
    """, nativeQuery = true)
    List<ServiceAggregation> aggregateServices(
            @Param("branchId") Long branchId,
            @Param("fromUtc") Instant fromUtc,
            @Param("toUtc") Instant toUtc
    );

    @Query(value = """
    SELECT
        COALESCE((
            SELECT SUM(o2.total)
            FROM sales_order o2
            WHERE o2.status = 'PAID'
              AND o2.paid_at_utc >= :fromUtc
              AND o2.paid_at_utc < :toUtc
              AND (:branchId IS NULL OR o2.branch_id = :branchId)
        ), 0) AS grossRevenue,

        COALESCE(SUM(
            CASE
                WHEN oi.item_type = 'PLAN'
                THEN oi.line_total
                ELSE 0
            END
        ), 0) AS planRevenue,

        COALESCE(SUM(
            CASE
                WHEN oi.item_type = 'PRODUCT'
                THEN oi.line_total
                ELSE 0
            END
        ), 0) AS productRevenue,

        COUNT(DISTINCT o.id) AS paidOrders

    FROM sales_order o
    LEFT JOIN sales_order_item oi
        ON oi.order_id = o.id

    WHERE o.status = 'PAID'
      AND o.paid_at_utc >= :fromUtc
      AND o.paid_at_utc < :toUtc
      AND (:branchId IS NULL OR o.branch_id = :branchId)
    """, nativeQuery = true)
    RevenueAggregation aggregateRevenue(
            @Param("branchId") Long branchId,
            @Param("fromUtc") Instant fromUtc,
            @Param("toUtc") Instant toUtc
    );
}