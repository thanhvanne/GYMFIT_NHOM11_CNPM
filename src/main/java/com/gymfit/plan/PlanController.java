package com.gymfit.plan;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.plan.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanManagementService planService;
    private final SecurityContextService securityContextService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PlanResponse> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) PlanStatus status
    ) {
        return planService.list(
                securityContextService.principal(),
                branchId,
                status
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PlanResponse get(@PathVariable Long id) {
        return planService.get(
                securityContextService.principal(),
                id
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PlanResponse create(
            @Valid @RequestBody PlanCreateRequest request
    ) {
        return planService.create(
                securityContextService.principal(),
                request
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlanResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PlanUpdateRequest request
    ) {
        return planService.update(
                securityContextService.principal(),
                id,
                request
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PlanResponse status(
            @PathVariable Long id,
            @Valid @RequestBody PlanStatusRequest request
    ) {
        return planService.updateStatus(
                securityContextService.principal(),
                id,
                request
        );
    }
}