package com.gymfit.order;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.order.dto.MemberPlanPurchaseRequest;
import com.gymfit.order.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member/purchases")
@RequiredArgsConstructor
public class MemberPurchaseController {

    private final MemberPurchaseService memberPurchaseService;
    private final SecurityContextService securityContextService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER')")
    public OrderResponse purchasePlan(
            @Valid @RequestBody MemberPlanPurchaseRequest request
    ) {
        return memberPurchaseService.purchasePlan(
                securityContextService.principal(),
                request
        );
    }
}