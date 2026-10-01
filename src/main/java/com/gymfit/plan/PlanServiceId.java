package com.gymfit.plan;

import com.gymfit.branch.ServiceCode;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class PlanServiceId implements Serializable {

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false, length = 20)
    private ServiceCode serviceCode;
}