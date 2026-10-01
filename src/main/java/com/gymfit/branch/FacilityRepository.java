package com.gymfit.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    List<Facility> findAllByBranchIdOrderByNameAsc(Long branchId);

    List<Facility> findAllByBranchIdAndServiceCodeAndStatusOrderByNameAsc(
            Long branchId,
            ServiceCode serviceCode,
            FacilityStatus status
    );

    Optional<Facility> findByBranchIdAndNameIgnoreCase(
            Long branchId,
            String name
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.id = :id")
    Optional<Facility> findByIdForUpdate(Long id);
}