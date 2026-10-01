package com.gymfit.membership;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository
        extends JpaRepository<Membership, Long> {

    Optional<Membership> findByMemberIdAndStatus(
            Long memberId,
            MembershipStatus status
    );

    List<Membership> findAllByMemberIdOrderByCreatedAtUtcDesc(
            Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from Membership m
            where m.memberId = :memberId
            and m.status = com.gymfit.membership.MembershipStatus.ACTIVE
            """)
    Optional<Membership> findActiveForUpdate(Long memberId);
}