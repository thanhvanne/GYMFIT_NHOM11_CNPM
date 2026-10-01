package com.gymfit.report;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.report.dto.DashboardResponse;
import com.gymfit.report.dto.RevenueReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.gymfit.report.dto.ServiceReportResponse;
import java.util.List;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
public class ReportController {

    private final ReportService reportService;
    private final SecurityContextService securityContextService;

    @GetMapping("/services")
    public List<ServiceReportResponse> services(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long branchId
    ) {
        return reportService.services(
                securityContextService.principal(),
                from,
                to,
                branchId
        );
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return reportService.dashboard(
                securityContextService.principal()
        );
    }

    @GetMapping("/revenue")
    public RevenueReportResponse revenue(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long branchId
    ) {
        return reportService.revenue(
                securityContextService.principal(),
                from,
                to,
                branchId
        );
    }
}