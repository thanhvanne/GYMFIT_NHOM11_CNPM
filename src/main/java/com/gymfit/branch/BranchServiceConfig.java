package com.gymfit.branch;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "branch_service_config")
public class BranchServiceConfig {

    @EmbeddedId
    private BranchServiceConfigId id;

    @Column(name = "booking_duration_minutes", nullable = false)
    private Integer bookingDurationMinutes;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "booking_enabled", nullable = false)
    private Boolean bookingEnabled;
}