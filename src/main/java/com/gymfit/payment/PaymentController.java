package com.gymfit.payment;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.payment.dto.CreateMomoPaymentRequest;
import com.gymfit.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SecurityContextService securityContextService;

    @PostMapping("/momo")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'MEMBER')"
    )
    public PaymentResponse createMomo(
            @Valid @RequestBody CreateMomoPaymentRequest request
    ) {
        return paymentService.createMomo(
                securityContextService.principal(),
                request
        );
    }

    @PostMapping("/{id}/simulate-success")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'MEMBER')"
    )
    public PaymentResponse simulateSuccess(
            @PathVariable Long id
    ) {
        return paymentService.simulateSuccess(
                securityContextService.principal(),
                id
        );
    }

    @PostMapping("/{id}/simulate-failure")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'MEMBER')"
    )
    public PaymentResponse simulateFailure(
            @PathVariable Long id
    ) {
        return paymentService.simulateFailure(
                securityContextService.principal(),
                id
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PaymentResponse get(
            @PathVariable Long id
    ) {
        return paymentService.get(
                securityContextService.principal(),
                id
        );
    }
}