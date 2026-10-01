package com.gymfit.member;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByMemberCodeIgnoreCase(String memberCode);

    Optional<Member> findByPhone(String phone);

    Optional<Member> findByEmailIgnoreCase(String email);

    List<Member> findAllByHomeBranchIdOrderByCreatedAtUtcDesc(Long branchId);

    List<Member> findAllByStatusOrderByCreatedAtUtcDesc(MemberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(Long id);
}