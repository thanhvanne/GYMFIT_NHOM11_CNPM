package com.gymfit.chat.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ChatTrainingCandidateRepository
        extends JpaRepository<ChatTrainingCandidate, Long> {

    Page<ChatTrainingCandidate> findByStatusOrderByCreatedAtUtcDesc(
            String status,
            Pageable pageable
    );

    List<ChatTrainingCandidate> findByStatusAndCreatedAtUtcAfterOrderByCreatedAtUtcDesc(
            String status,
            Instant after
    );

    List<ChatTrainingCandidate> findByStatusOrderByCreatedAtUtcDesc(
            String status
    );

    /** 20 câu fallback mới nhất – dùng cho trang quản trị. */
    List<ChatTrainingCandidate> findTop20ByStatusOrderByCreatedAtUtcDesc(
            String status
    );

    long countByStatus(
            String status
    );

    long countByStatusAndCreatedAtUtcAfter(
            String status,
            Instant after
    );

    long countByCreatedAtUtcAfter(
            Instant after
    );
}