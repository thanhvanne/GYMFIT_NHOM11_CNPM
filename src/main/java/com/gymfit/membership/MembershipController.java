package com.gymfit.membership;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.membership.dto.MembershipResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members/{memberId}/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;
    private final SecurityContextService securityContextService;

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public MembershipResponse current(
            @PathVariable Long memberId
    ) {
        return membershipService.current(
                securityContextService.principal(),
                memberId
        );
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MembershipResponse> history(
            @PathVariable Long memberId
    ) {
        return membershipService.history(
                securityContextService.principal(),
                memberId
        );
    }
}