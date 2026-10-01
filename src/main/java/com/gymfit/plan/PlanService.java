package com.gymfit.plan;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan_service")
public class PlanService {

    @EmbeddedId
    private PlanServiceId id;
}