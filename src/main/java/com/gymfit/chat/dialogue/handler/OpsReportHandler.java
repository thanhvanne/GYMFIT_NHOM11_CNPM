package com.gymfit.chat.dialogue.handler;

import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.BookingStatus;
import com.gymfit.checkin.dto.CheckInResponse;
import com.gymfit.checkin.CheckInResult;
import com.gymfit.checkin.CheckInService;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.inventory.InventoryService;
import com.gymfit.inventory.dto.InventoryResponse;
import com.gymfit.report.ReportService;
import com.gymfit.report.dto.DashboardResponse;
import com.gymfit.report.dto.RevenueReportResponse;
import com.gymfit.report.dto.ServiceReportResponse;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Báo cáo vận hành cho BRANCH_MANAGER và ADMIN.
 * <p>Phạm vi chi nhánh: MANAGER luôn bị ép theo chi nhánh của mình (service tự
 * kiểm tra → ném {@code ForbiddenException}); ADMIN dùng branchId trong câu
 * hoặc toàn hệ thống (null).
 */
@Component
@RequiredArgsConstructor
public class OpsReportHandler
        implements IntentHandler {

    private final ReportService reportService;
    private final BookingService bookingService;
    private final CheckInService checkInService;
    private final InventoryService inventoryService;
    private final ResponseTemplates templates;

    @Value("${gymfit.chatbot.low-stock-threshold:10}")
    private int lowStockThreshold =
            10;

    @Override
    public Set<Intent> supports() {
        return Set.of(
                Intent.REPORT_DASHBOARD,
                Intent.REPORT_REVENUE,
                Intent.REPORT_SERVICE,
                Intent.LOW_STOCK,
                Intent.BOOKINGS_TODAY,
                Intent.CHECKINS_REJECTED
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        try {

            Long branchId =
                    branchScope(
                            context
                    );

            String label =
                    branchLabel(
                            context,
                            branchId
                    );

            return switch (context.intent()) {
                case REPORT_DASHBOARD ->
                        dashboard(
                                context
                        );

                case REPORT_REVENUE ->
                        revenue(
                                context,
                                branchId,
                                label
                        );

                case REPORT_SERVICE ->
                        serviceReport(
                                context,
                                branchId,
                                label
                        );

                case LOW_STOCK ->
                        lowStock(
                                context,
                                branchId,
                                label
                        );

                case BOOKINGS_TODAY ->
                        bookingsToday(
                                context,
                                label
                        );

                default ->
                        rejectedCheckIns(
                                context,
                                branchId,
                                label
                        );
            };

        } catch (ForbiddenException exception) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "denied.branch"
                    )
            );

        } catch (ApiException exception) {

            return fail(
                    context,
                    exception
            );
        }
    }

    // ------------------------------------------------------------------
    // Phạm vi chi nhánh
    // ------------------------------------------------------------------

    private Long branchScope(
            HandlerContext context
    ) {

        if (context.principal()
                .getRole() == RoleCode.ADMIN) {

            return context.entities()
                    .branchId();
        }

        // Manager: service sẽ tự ép theo chi nhánh
        return context.principal()
                .getBranchId();
    }

    private String branchLabel(
            HandlerContext context,
            Long branchId
    ) {

        if (context.principal()
                .getRole() == RoleCode.ADMIN
                && branchId == null) {
            return "toàn hệ thống";
        }

        return branchId == null
                ? "chi nhánh của bạn"
                : "chi nhánh #" + branchId;
    }

    // ------------------------------------------------------------------

    private ChatResponse dashboard(
            HandlerContext context
    ) {

        DashboardResponse data =
                reportService.dashboard(
                        context.principal()
                );

        return IntentHandler.message(
                context,
                templates.get(
                        "report.dashboard",
                        Map.of(
                                "branch_label",
                                branchLabel(
                                        context,
                                        branchScope(
                                                context
                                        )
                                ),
                                "revenue",
                                Fmt.money(
                                        data.revenue()
                                ),
                                "members",
                                data.members(),
                                "bookings",
                                data.bookings(),
                                "checkIns",
                                data.checkIns(),
                                "activeMemberships",
                                data.activeMemberships()
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse revenue(
            HandlerContext context,
            Long branchId,
            String label
    ) {

        LocalDate today =
                context.today();

        LocalDate from =
                context.entities()
                        .rangeFrom() != null
                        ? context.entities()
                        .rangeFrom()
                        : YearMonth.from(today)
                        .atDay(1);

        LocalDate to =
                context.entities()
                        .rangeTo() != null
                        ? context.entities()
                        .rangeTo()
                        : today;

        RevenueReportResponse data =
                reportService.revenue(
                        context.principal(),
                        from,
                        to,
                        branchId
                );

        return IntentHandler.message(
                context,
                templates.get(
                        "report.revenue",
                        Map.of(
                                "from",
                                Fmt.shortDate(
                                        from
                                ),
                                "to",
                                Fmt.shortDate(
                                        to
                                ),
                                "branch_label",
                                label,
                                "gross",
                                Fmt.money(
                                        data.grossRevenue()
                                ),
                                "plan",
                                Fmt.money(
                                        data.planRevenue()
                                ),
                                "product",
                                Fmt.money(
                                        data.productRevenue()
                                ),
                                "orders",
                                data.paidOrders()
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse serviceReport(
            HandlerContext context,
            Long branchId,
            String label
    ) {

        LocalDate today =
                context.today();

        LocalDate from =
                context.entities()
                        .hasRange()
                        ? context.entities()
                        .rangeFrom()
                        : YearMonth.from(today)
                        .atDay(1);

        LocalDate to =
                context.entities()
                        .hasRange()
                        ? context.entities()
                        .rangeTo()
                        : today;

        List<ServiceReportResponse> rows =
                reportService.services(
                                context.principal(),
                                from,
                                to,
                                branchId
                        )
                        .stream()
                        .sorted(
                                Comparator.comparingLong(
                                        ServiceReportResponse::bookings
                                )
                                .reversed()
                        )
                        .toList();

        if (rows.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "info.no_data"
                    )
            );
        }

        String body =
                rows.stream()
                        .map(row ->
                                "• " + Fmt.service(
                                        row.serviceCode()
                                ) + ": " + row.bookings()
                                        + " lượt đặt, " + row.checkIns()
                                        + " lượt check-in")
                        .collect(
                                Collectors.joining("\n")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "report.service",
                        Map.of(
                                "from",
                                Fmt.shortDate(
                                        from
                                ),
                                "to",
                                Fmt.shortDate(
                                        to
                                ),
                                "branch_label",
                                label,
                                "rows",
                                body
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse lowStock(
            HandlerContext context,
            Long branchId,
            String label
    ) {

        List<InventoryResponse> all =
                inventoryService.list(
                        context.principal(),
                        branchId
                );

        if (all == null) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "stock.none",
                            Map.of(
                                    "branch_label",
                                    label
                            )
                    )
            );
        }

        List<InventoryResponse> low =
                all.stream()
                        .filter(item ->
                                item.quantity() != null
                                        && item.quantity()
                                        <= lowStockThreshold
                        )
                        .sorted(
                                Comparator.comparing(
                                        InventoryResponse::quantity
                                )
                        )
                        .limit(10)
                        .toList();

        if (low.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "stock.none",
                            Map.of(
                                    "branch_label",
                                    label
                            )
                    )
            );
        }

        String body =
                low.stream()
                        .map(item ->
                                "• " + item.productName()
                                        + " (" + item.sku() + "): còn "
                                        + item.quantity())
                        .collect(
                                Collectors.joining("\n")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "stock.low",
                        Map.of(
                                "branch_label",
                                label,
                                "rows",
                                body
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse bookingsToday(
            HandlerContext context,
            String label
    ) {

        LocalDate today =
                context.today();

        List<BookingResponse> list =
                bookingService.list(
                        context.principal()
                );

        List<BookingResponse> todayList =
                list.stream()
                        .filter(booking ->
                                booking.startsAtUtc()
                                        .atZone(
                                                TimeUtil.VIETNAM
                                        )
                                        .toLocalDate()
                                        .equals(today)
                        )
                        .toList();

        if (todayList.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "bookings.today.none",
                            Map.of(
                                    "branch_label",
                                    label
                            )
                    )
            );
        }

        Map<String, Long> byStatus =
                todayList.stream()
                        .collect(
                                Collectors.groupingBy(
                                        booking ->
                                                booking.status()
                                                        .name(),
                                        Collectors.counting()
                                )
                        );

        Map<String, Long> byService =
                todayList.stream()
                        .collect(
                                Collectors.groupingBy(
                                        booking ->
                                                Fmt.service(
                                                        booking.serviceCode()
                                                ),
                                        Collectors.counting()
                                )
                        );

        String summary =
                "• Theo trạng thái: "
                        + byStatus.entrySet()
                        .stream()
                        .map(entry ->
                                entry.getKey() + "=" + entry.getValue())
                        .collect(
                                Collectors.joining(", ")
                        )
                        + "\n• Theo dịch vụ: "
                        + byService.entrySet()
                        .stream()
                        .map(entry ->
                                entry.getKey() + "=" + entry.getValue())
                        .collect(
                                Collectors.joining(", ")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "bookings.today",
                        Map.of(
                                "branch_label",
                                label,
                                "total",
                                todayList.size(),
                                "summary",
                                summary
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse rejectedCheckIns(
            HandlerContext context,
            Long branchId,
            String label
    ) {

        LocalDate from =
                context.entities()
                        .hasRange()
                        ? context.entities()
                        .rangeFrom()
                        : context.today();

        LocalDate to =
                context.entities()
                        .hasRange()
                        ? context.entities()
                        .rangeTo()
                        : context.today();

        List<CheckInResponse> rejected =
                checkInService.list(
                                context.principal()
                        )
                        .stream()
                        .filter(checkIn ->
                                checkIn.result()
                                        == CheckInResult.REJECTED
                        )
                        .filter(checkIn -> {
                            LocalDate date =
                                    checkIn.createdAtUtc()
                                            .atZone(
                                                    TimeUtil.VIETNAM
                                            )
                                            .toLocalDate();

                            return !date.isBefore(
                                    from
                            )
                                    && !date.isAfter(
                                    to
                            );
                        })
                        .toList();

        if (rejected.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "checkin.rejected_none",
                            Map.of(
                                    "range_label",
                                    rangeLabel(
                                            from,
                                            to
                                    ),
                                    "branch_label",
                                    label
                            )
                    )
            );
        }

        Map<String, Long> byReason =
                rejected.stream()
                        .collect(
                                Collectors.groupingBy(
                                        checkIn ->
                                                Fmt.rejectReason(
                                                        checkIn.reason()
                                                ),
                                        Collectors.counting()
                                )
                        );

        String rows =
                byReason.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry.<String, Long>comparingByValue()
                                        .reversed()
                        )
                        .map(entry ->
                                "• " + entry.getKey() + ": " + entry.getValue())
                        .collect(
                                Collectors.joining("\n")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "checkin.rejected_summary",
                        Map.of(
                                "range_label",
                                rangeLabel(
                                        from,
                                        to
                                ),
                                "branch_label",
                                label,
                                "rows",
                                rows
                        )
                )
        );
    }

    private String rangeLabel(
            LocalDate from,
            LocalDate to
    ) {

        if (from.equals(to)) {
            return Fmt.date(
                    from
            );
        }

        return Fmt.shortDate(
                from
        ) + " – " + Fmt.shortDate(
                to
        );
    }
}