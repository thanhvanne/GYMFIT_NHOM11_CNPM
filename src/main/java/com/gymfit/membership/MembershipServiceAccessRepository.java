package com.gymfit.membership;

import com.gymfit.branch.ServiceCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipServiceAccessRepository
        extends JpaRepository<
        MembershipServiceAccess,
        MembershipServiceAccessId
        > {

    List<MembershipServiceAccess> findAllByIdMembershipId(
            Long membershipId
    );

    boolean existsByIdMembershipIdAndIdServiceCode(
            Long membershipId,
            ServiceCode serviceCode
    );
}