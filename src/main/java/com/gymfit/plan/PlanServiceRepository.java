package com.gymfit.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanServiceRepository
        extends JpaRepository<PlanService, PlanServiceId> {

    List<PlanService> findAllByIdPlanId(Long planId);

    void deleteAllByIdPlanId(Long planId);
}