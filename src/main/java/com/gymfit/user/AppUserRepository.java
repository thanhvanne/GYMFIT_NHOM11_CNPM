package com.gymfit.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByMemberId(Long memberId);

    List<AppUser> findAllByOrderByCreatedAtUtcDesc();

    List<AppUser> findAllByRoleCodeOrderByCreatedAtUtcDesc(
            RoleCode roleCode
    );
}