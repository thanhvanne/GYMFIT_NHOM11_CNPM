package com.gymfit.branch;

import com.gymfit.branch.dto.*;
import com.gymfit.common.security.SecurityContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final SecurityContextService securityContextService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BranchResponse> list(
            @RequestParam(required = false) BranchStatus status
    ) {
        return branchService.list(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BranchResponse get(@PathVariable Long id) {
        return branchService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponse create(
            @Valid @RequestBody BranchCreateRequest request
    ) {
        return branchService.create(
                securityContextService.principal().getUserId(),
                request
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BranchUpdateRequest request
    ) {
        return branchService.update(
                securityContextService.principal().getUserId(),
                id,
                request
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BranchStatusRequest request
    ) {
        return branchService.updateStatus(
                securityContextService.principal().getUserId(),
                id,
                request
        );
    }

    @GetMapping("/{id}/services")
    @PreAuthorize("isAuthenticated()")
    public List<BranchServiceResponse> services(
            @PathVariable Long id
    ) {
        return branchService.getServices(id);
    }

    @PutMapping("/{id}/services")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BranchServiceResponse> updateServices(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchServicesRequest request
    ) {
        return branchService.updateServices(
                securityContextService
                        .principal()
                        .getUserId(),
                id,
                request
        );
    }

    @GetMapping("/{id}/operating-hours")
    @PreAuthorize("isAuthenticated()")
    public List<OperatingHourResponse> operatingHours(
            @PathVariable Long id
    ) {
        return branchService.getOperatingHours(id);
    }

    @PutMapping("/{id}/operating-hours")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OperatingHourResponse> updateOperatingHours(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOperatingHoursRequest request
    ) {
        return branchService.updateOperatingHours(
                securityContextService
                        .principal()
                        .getUserId(),
                id,
                request
        );
    }
}