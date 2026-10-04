package com.gymfit.membership;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MembershipRepository
        extends JpaRepository<Membership, Long> {

    List<Membership> findAllByMemberIdAndStatusOrderByEndDateAscCreatedAtUtcDesc(
            Long memberId,
            MembershipStatus status
    );

    List<Membership> findAllByMemberIdOrderByCreatedAtUtcDesc(
            Long memberId
    );

    /**
     * Khóa toàn bộ các gói đang hoạt động của hội viên khi kích hoạt gói mới.
     * Không còn giả định mỗi hội viên chỉ có một membership ACTIVE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from Membership m
            where m.memberId = :memberId
            and m.status = com.gymfit.membership.MembershipStatus.ACTIVE
            order by m.endDate asc, m.createdAtUtc desc
            """)
    List<Membership> findActiveForUpdate(Long memberId);
}
