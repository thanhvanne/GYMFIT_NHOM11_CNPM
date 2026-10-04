package com.gymfit.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByMemberId(Long memberId);

    /**
     * F5 – nạp tài khoản của N hội viên trong MỘT câu lệnh (tránh N+1).
     */
    List<AppUser> findAllByMemberIdIn(Collection<Long> memberIds);

    List<AppUser> findAllByOrderByCreatedAtUtcDesc();

    List<AppUser> findAllByRoleCodeOrderByCreatedAtUtcDesc(
            RoleCode roleCode
    );
}