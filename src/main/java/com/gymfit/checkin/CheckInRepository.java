package com.gymfit.checkin;

import com.gymfit.branch.ServiceCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CheckInRepository
        extends JpaRepository<CheckIn, Long> {

    boolean existsByMemberIdAndBranchIdAndServiceCodeAndResultAndCreatedAtUtcAfter(
            Long memberId,
            Long branchId,
            ServiceCode serviceCode,
            CheckInResult result,
            Instant after
    );

    List<CheckIn> findAllByBranchIdOrderByCreatedAtUtcDesc(
            Long branchId
    );

    List<CheckIn> findAllByMemberIdOrderByCreatedAtUtcDesc(
            Long memberId
    );

    List<CheckIn> findAllByOrderByCreatedAtUtcDesc();
}