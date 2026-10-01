package com.gymfit.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchOperatingHourRepository
        extends JpaRepository<BranchOperatingHour, Long> {

    List<BranchOperatingHour> findAllByBranchIdOrderByDayOfWeekAsc(Long branchId);

    Optional<BranchOperatingHour> findByBranchIdAndDayOfWeek(
            Long branchId,
            Integer dayOfWeek
    );

    void deleteAllByBranchId(Long branchId);
}