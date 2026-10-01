package com.gymfit.membership;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "membership_service_access")
public class MembershipServiceAccess {

    @EmbeddedId
    private MembershipServiceAccessId id;
}