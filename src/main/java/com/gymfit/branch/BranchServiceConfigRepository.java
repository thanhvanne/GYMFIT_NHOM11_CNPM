package com.gymfit.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchServiceConfigRepository
        extends JpaRepository<BranchServiceConfig, BranchServiceConfigId> {

    List<BranchServiceConfig> findAllByIdBranchIdOrderByIdServiceCodeAsc(Long branchId);

    boolean existsByIdBranchIdAndIdServiceCode(
            Long branchId,
            ServiceCode serviceCode
    );

    void deleteAllByIdBranchId(Long branchId);

    Optional<BranchServiceConfig> findByIdBranchIdAndIdServiceCode(
            Long branchId,
            ServiceCode serviceCode
    );
}