package com.gymfit.report.dto;

import java.math.BigDecimal;

public record DashboardResponse(
        BigDecimal revenue,
        long members,
        long bookings,
        long checkIns,
        long activeMemberships
) {
}