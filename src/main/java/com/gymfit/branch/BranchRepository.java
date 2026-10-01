package com.gymfit.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Branch> findAllByStatusOrderByNameAsc(BranchStatus status);

    List<Branch> findAllByOrderByNameAsc();
}