package com.gymfit.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository
        extends JpaRepository<StockLevel, Long> {

    List<StockLevel> findAllByBranchIdOrderByProductIdAsc(Long branchId);

    Optional<StockLevel> findByBranchIdAndProductId(
            Long branchId,
            Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s
        from StockLevel s
        where s.branchId = :branchId
        and s.productId = :productId
        """)
    Optional<StockLevel> findForUpdate(
            Long branchId,
            Long productId
    );
}