package com.gymfit.audit;

import com.gymfit.audit.dto.AuditEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEventResponse> list(
            @RequestParam(required = false) Long branchId
    ) {
        return auditService.list(branchId);
    }
}