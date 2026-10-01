package com.gymfit.report.dto;

import com.gymfit.branch.ServiceCode;

public record ServiceReportResponse(
        ServiceCode serviceCode,
        long bookings,
        long checkIns
) {
}