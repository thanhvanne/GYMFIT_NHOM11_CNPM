package com.gymfit.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipPlanRepository
        extends JpaRepository<MembershipPlan, Long> {

    Optional<MembershipPlan> findByPlanCodeIgnoreCase(String planCode);

    List<MembershipPlan> findAllByBranchIdOrderByPriceAsc(Long branchId);

    List<MembershipPlan> findAllByBranchIdAndStatusOrderByPriceAsc(
            Long branchId,
            PlanStatus status
    );

    List<MembershipPlan> findAllByStatusOrderByBranchIdAscPriceAsc(
            PlanStatus status
    );
}