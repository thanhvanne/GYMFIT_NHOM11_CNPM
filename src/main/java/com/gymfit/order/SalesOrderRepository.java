package com.gymfit.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository
        extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findByOrderCode(String orderCode);

    List<SalesOrder> findAllByBranchIdOrderByCreatedAtUtcDesc(
            Long branchId
    );

    List<SalesOrder> findAllByMemberIdOrderByCreatedAtUtcDesc(
            Long memberId
    );

    List<SalesOrder> findAllByOrderByCreatedAtUtcDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from SalesOrder o
        where o.id = :id
        """)
    Optional<SalesOrder> findByIdForUpdate(Long id);
}