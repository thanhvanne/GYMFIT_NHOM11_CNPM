package com.gymfit.checkin;

import com.gymfit.checkin.dto.*;
import com.gymfit.common.security.SecurityContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkins")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;
    private final SecurityContextService securityContextService;

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public CheckInResponse manual(
            @Valid @RequestBody ManualCheckInRequest request
    ) {
        return checkInService.manual(
                securityContextService.principal(),
                request
        );
    }

    @PostMapping("/qr")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public CheckInResponse qr(
            @Valid @RequestBody QrCheckInRequest request
    ) {
        return checkInService.qr(
                securityContextService.principal(),
                request
        );
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CheckInResponse> list() {
        return checkInService.list(
                securityContextService.principal()
        );
    }
}