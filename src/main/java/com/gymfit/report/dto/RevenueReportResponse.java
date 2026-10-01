package com.gymfit.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueReportResponse(
        LocalDate from,
        LocalDate to,
        Long branchId,
        BigDecimal grossRevenue,
        BigDecimal planRevenue,
        BigDecimal productRevenue,
        long paidOrders
) {
}