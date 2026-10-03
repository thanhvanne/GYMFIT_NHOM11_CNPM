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
import com.gymfit.common.util.TimeUtil;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.MembershipStatus;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.order.dto.OrderResponse;
import com.gymfit.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dữ liệu cá nhân của hội viên: gói tập, lịch, check-in, đơn hàng.
 * <p>Tất cả service đã tự giới hạn theo {@code principal} — handler chỉ lọc
 * và định dạng.
 */
@Component
@RequiredArgsConstructor
public class MemberInfoHandler
        implements IntentHandler {

    private final MembershipService membershipService;
    private final BookingService bookingService;
    private final CheckInService checkInService;
    private final OrderService orderService;
    private final ResponseTemplates templates;

    @Override
    public Set<Intent> supports() {
        return Set.of(
                Intent.MY_MEMBERSHIP,
                Intent.MY_BOOKINGS,
                Intent.MY_CHECKINS,
                Intent.MY_ORDERS
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        Long memberId =
                context.principal()
                        .getMemberId();

        if (memberId == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "membership.none"
                    )
            );
        }

        try {

            return switch (context.intent()) {
                case MY_MEMBERSHIP ->
                        membership(
                                context,
                                memberId
                        );

                case MY_BOOKINGS ->
                        bookings(
                                context,
                                memberId
                        );

                case MY_CHECKINS ->
                        checkIns(
                                context,
                                memberId
                        );

                default ->
                        orders(
                                context,
                                memberId
                        );
            };

        } catch (ApiException exception) {

            return fail(
                    context,
                    exception
            );
        }
    }

    // ------------------------------------------------------------------

    private ChatResponse membership(
            HandlerContext context,
            Long memberId
    ) {

        MembershipResponse membership =
                membershipService.current(
                        context.principal(),
                        memberId
                );

        if (membership == null) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "membership.none"
                    ),
                    List.of(
                            ChatSuggestion.of(
                                    "Xem gói tập"
                            )
                    )
            );
        }

        String services =
                membership.services()
                        .stream()
                        .map(
                                Fmt::service)
                        .collect(
                                Collectors.joining(", ")
                        );

        long days =
                membership.endDate() == null
                        ? 0
                        : java.time.temporal.ChronoUnit.DAYS.between(
                        context.today(),
                        membership.endDate()
                );

        return IntentHandler.message(
                context,
                templates.get(
                        "membership.active",
                        Map.of(
                                "plan_name",
                                planName(
                                        membership
                                ),
                                "tier",
                                membership.planId() == null
                                        ? ""
                                        : "gói #"
                                        + membership.planId(),
                                "from",
                                Fmt.shortDate(
                                        membership.startDate()
                                ),
                                "to",
                                Fmt.shortDate(
                                        membership.endDate()
                                ),
                                "days",
                                Math.max(
                                        0,
                                        days
                                ),
                                "branch",
                                membership.branchId() == null
                                        ? "chưa xác định"
                                        : "chi nhánh #"
                                        + membership.branchId(),
                                "services",
                                services
                        )
                ),
                List.of(
                        ChatSuggestion.of(
                                "Lịch sắp tới"
                        ),
                        ChatSuggestion.of(
                                "Đặt lịch tập"
                        )
                )
        );
    }

    private String planName(
            MembershipResponse membership
    ) {
        return membership.status() == MembershipStatus.ACTIVE
                ? "đang hoạt động"
                : String.valueOf(
                membership.status()
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse bookings(
            HandlerContext context,
            Long memberId
    ) {

        Instant now =
                TimeUtil.now();

        List<BookingResponse> upcoming =
                bookingService.list(
                                context.principal()
                        )
                        .stream()
                        .filter(booking ->
                                booking.status()
                                        == BookingStatus.CONFIRMED
                        )
                        .filter(booking ->
                                booking.startsAtUtc()
                                        .isAfter(now)
                        )
                        .sorted(
                                Comparator.comparing(
                                        BookingResponse::startsAtUtc
                                )
                        )
                        .limit(10)
                        .toList();

        if (upcoming.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "my.bookings.none"
                    ),
                    List.of(
                            ChatSuggestion.of(
                                    "Đặt lịch tập"
                            )
                    )
            );
        }

        String body =
                upcoming.stream()
                        .map(booking ->
                                "• " + Fmt.dateTime(
                                        booking.startsAtUtc(),
                                        TimeUtil.VIETNAM
                                ) + " — " + Fmt.service(
                                        booking.serviceCode()
                                ) + " (mã " + booking.bookingCode() + ")")
                        .collect(
                                Collectors.joining("\n")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "my.bookings",
                        Map.of(
                                "bookings",
                                body
                        )
                ),
                List.of(
                        ChatSuggestion.of(
                                "Hủy lịch của tôi"
                        ),
                        ChatSuggestion.of(
                                "Xem lịch sử check-in"
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse checkIns(
            HandlerContext context,
            Long memberId
    ) {

        List<CheckInResponse> list =
                checkInService.list(
                                context.principal()
                        )
                        .stream()
                        .limit(5)
                        .toList();

        if (list.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "my.checkins.none"
                    )
            );
        }

        String body =
                list.stream()
                        .map(checkIn ->
                                "• " + Fmt.dateTime(
                                        checkIn.createdAtUtc(),
                                        TimeUtil.VIETNAM
                                ) + " — " + Fmt.service(
                                        checkIn.serviceCode()
                                ) + " — "
                                        + (checkIn.result()
                                        == CheckInResult.ACCEPTED
                                        ? "thành công"
                                        : "từ chối: "
                                        + Fmt.rejectReason(
                                        checkIn.reason())))
                        .collect(
                                Collectors.joining("\n")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "my.checkins",
                        Map.of(
                                "checkins",
                                body
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private ChatResponse orders(
            HandlerContext context,
            Long memberId
    ) {

        List<OrderResponse> list =
                orderService.list(
                                context.principal()
                        )
                        .stream()
                        .limit(5)
                        .toList();

        if (list.isEmpty()) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "my.orders.none"
                    )
            );
        }

        String body =
                list.stream()
                        .map(order ->
                                "• " + Fmt.shortDate(
                                        order.paidAtUtc() == null
                                                ? null
                                                : order.paidAtUtc()
                                                .atZone(
                                                        TimeUtil.VIETNAM
                                                )
                                                .toLocalDate()
                                ) + " — " + order.orderCode()
                                        + " — " + Fmt.money(
                                        order.total())
                                        + " (" + order.status() + ")")
                        .collect(
                                Collectors.joining("\n")
                        );

        return IntentHandler.message(
                context,
                templates.get(
                        "my.orders",
                        Map.of(
                                "orders",
                                body
                        )
                )
        );
    }
}