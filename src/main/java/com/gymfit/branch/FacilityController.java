package com.gymfit.branch;

import com.gymfit.branch.dto.FacilityCreateRequest;
import com.gymfit.branch.dto.FacilityResponse;
import com.gymfit.branch.dto.FacilityUpdateRequest;
import com.gymfit.common.security.SecurityContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService facilityService;
    private final SecurityContextService securityContextService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<FacilityResponse> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ServiceCode serviceCode,
            @RequestParam(required = false) FacilityStatus status
    ) {
        return facilityService.list(
                securityContextService.principal(),
                branchId,
                serviceCode,
                status
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public FacilityResponse get(@PathVariable Long id) {
        return facilityService.get(
                securityContextService.principal(),
                id
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public FacilityResponse create(
            @Valid @RequestBody FacilityCreateRequest request
    ) {
        return facilityService.create(
                securityContextService.principal(),
                request
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public FacilityResponse update(
            @PathVariable Long id,
            @Valid @RequestBody FacilityUpdateRequest request
    ) {
        return facilityService.update(
                securityContextService.principal(),
                id,
                request
        );
    }
}