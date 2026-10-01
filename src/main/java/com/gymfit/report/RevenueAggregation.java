package com.gymfit.report;

import java.math.BigDecimal;

public interface RevenueAggregation {

    BigDecimal getGrossRevenue();

    BigDecimal getPlanRevenue();

    BigDecimal getProductRevenue();

    Long getPaidOrders();
}