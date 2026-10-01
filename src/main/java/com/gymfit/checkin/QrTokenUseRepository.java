package com.gymfit.checkin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QrTokenUseRepository
        extends JpaRepository<QrTokenUse, Long> {

    boolean existsByTokenJti(String tokenJti);
}