package com.gymfit.membership;

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
public class MembershipServiceAccessId implements Serializable {

    @Column(name = "membership_id", nullable = false)
    private Long membershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false, length = 20)
    private ServiceCode serviceCode;
}