package com.gymfit.report;

import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.MoneyUtil;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.report.dto.DashboardResponse;
import com.gymfit.report.dto.RevenueReportResponse;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymfit.branch.ServiceCode;
import com.gymfit.report.dto.ServiceReportResponse;

import java.util.List;

import java.math.BigDecimal;
import java.time.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final DashboardAggregationRepository dashboardRepository;
    private final BranchScopeGuard branchScopeGuard;

    @Transactional(readOnly = true)
    public List<ServiceReportResponse> services(
            AppPrincipal principal,
            LocalDate from,
            LocalDate to,
            Long requestedBranchId
    ) {
        if (to.isBefore(from)) {
            throw new com.gymfit.common.error.BadRequestException(
                    "invalid_date_range",
                    "Ngày kết thúc phải từ ngày bắt đầu trở đi"
            );
        }

        Long branchId =
                scopeBranch(principal, requestedBranchId);

        Instant fromUtc = from
                .atStartOfDay(TimeUtil.VIETNAM)
                .toInstant();

        Instant toUtc = to
                .plusDays(1)
                .atStartOfDay(TimeUtil.VIETNAM)
                .toInstant();

        return reportRepository
                .aggregateServices(
                        branchId,
                        fromUtc,
                        toUtc
                )
                .stream()
                .map(row ->
                        new ServiceReportResponse(
                                ServiceCode.valueOf(
                                        row.getServiceCode()
                                ),
                                row.getBookings() == null
                                        ? 0
                                        : row.getBookings(),
                                row.getCheckIns() == null
                                        ? 0
                                        : row.getCheckIns()
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(
            AppPrincipal principal
    ) {
        Long branchId = scopeBranch(principal, null);

        LocalDate today =
                LocalDate.now(TimeUtil.VIETNAM);

        Instant fromUtc = today
                .atStartOfDay(TimeUtil.VIETNAM)
                .toInstant();

        Instant toUtc = today
                .plusDays(1)
                .atStartOfDay(TimeUtil.VIETNAM)
                .toInstant();

        BigDecimal revenue =
                dashboardRepository.revenue(
                        branchId,
                        fromUtc,
                        toUtc
                );

        return new DashboardResponse(
                MoneyUtil.normalize(revenue),
                dashboardRepository.activeMembers(branchId),
                dashboardRepository.bookings(
                        branchId,
                        fromUtc,
                        toUtc
                ),
                dashboardRepository.checkIns(
                        branchId,
                        fromUtc,
                        toUtc
                ),
                dashboardRepository.activeMemberships(
                        branchId,
                        today
                )
        );
    }

    @Transactional(readOnly = true)
    public RevenueReportResponse revenue(
            AppPrincipal principal,
            LocalDate from,
            LocalDate to,
            Long requestedBranchId
    ) {
        if (to.isBefore(from)) {
            throw new com.gymfit.common.error.BadRequestException(
                    "invalid_date_range",
                    "Ngày kết thúc phải từ ngày bắt đầu trở đi"
            );
        }

        Long branchId =
                scopeBranch(principal, requestedBranchId);

        Instant fromUtc = from
                .atStartOfDay(TimeUtil.VIETNAM)
                .toInstant();

        Instant toUtc = to
                .plusDays(1)
                .atStartOfDay(TimeUtil.VIETNAM)
                .toInstant();

        RevenueAggregation aggregation =
                reportRepository.aggregateRevenue(
                        branchId,
                        fromUtc,
                        toUtc
                );

        return new RevenueReportResponse(
                from,
                to,
                branchId,
                MoneyUtil.normalize(
                        aggregation.getGrossRevenue()
                ),
                MoneyUtil.normalize(
                        aggregation.getPlanRevenue()
                ),
                MoneyUtil.normalize(
                        aggregation.getProductRevenue()
                ),
                aggregation.getPaidOrders() == null
                        ? 0
                        : aggregation.getPaidOrders()
        );
    }

    private Long scopeBranch(
            AppPrincipal principal,
            Long requestedBranchId
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return requestedBranchId;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            if (requestedBranchId != null) {
                branchScopeGuard.requireBranch(
                        principal,
                        requestedBranchId
                );
            }

            return principal.getBranchId();
        }

        throw new ForbiddenException(
                "report_forbidden",
                "Bạn không có quyền xem báo cáo"
        );
    }
}