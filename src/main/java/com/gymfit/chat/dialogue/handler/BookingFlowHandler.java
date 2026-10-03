package com.gymfit.chat.dialogue.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.BookingStatus;
import com.gymfit.booking.dto.AvailabilitySlotResponse;
import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.FacilityService;
import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.ServiceCode;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.branch.dto.BranchServiceResponse;
import com.gymfit.branch.dto.FacilityResponse;
import com.gymfit.chat.dialogue.Awaiting;
import com.gymfit.chat.dialogue.ConversationState;
import com.gymfit.chat.dialogue.PendingAction;
import com.gymfit.chat.dto.ChatCard;
import com.gymfit.chat.dto.ChatCardLine;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Luồng đặt / hủy lịch — intent ghi dữ liệu.
 *
 * <p>Không bao giờ ghi thẳng: handler chỉ <b>lập kế hoạch</b>, lưu
 * {@link PendingAction} vào trạng thái hội thoại và trả thẻ {@code CONFIRM}.
 * Việc thực thi do {@code BookingActionExecutor} đảm nhiệm khi người dùng bấm
 * Xác nhận (payload {@code CONFIRM:<uuid>}), nên không thể ghi dữ liệu chỉ vì
 * mô hình đoán nhầm intent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingFlowHandler
        implements IntentHandler {

    public static final String SLOT_SERVICE =
            "service";

    public static final String SLOT_DATE =
            "date";

    public static final String SLOT_TIME =
            "time";

    public static final String SLOT_BRANCH =
            "branch";

    public static final String SLOT_BOOKING_ID =
            "booking_id";

    public static final String SLOT_BOOKING_CODE =
            "booking_code";

    private static final String SLOT_ASKED_PICK =
            "asked_pick";

    private final BookingService bookingService;

    private final FacilityService facilityService;

    private final BranchService branchService;

    private final MembershipService membershipService;

    private final ResponseTemplates templates;

    private final BranchSupport branchSupport;

    private final ObjectMapper objectMapper;

    @Value("${gymfit.chatbot.pending-action-ttl-minutes:5}")
    private int pendingTtlMinutes =
            5;

    @Override
    public Set<Intent> supports() {
        return Set.of(
                Intent.BOOKING_CREATE,
                Intent.BOOKING_CANCEL,
                Intent.BOOKING_AVAILABILITY
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        ConversationState state =
                context.stateOrNew();

        try {

            return switch (context.intent()) {
                case BOOKING_CREATE ->
                        create(
                                context,
                                state
                        );

                case BOOKING_CANCEL ->
                        cancel(
                                context,
                                state
                        );

                default ->
                        availability(
                                context,
                                state
                        );
            };

        } catch (ApiException exception) {

            // Hết lượt hỏi → không kẹt phiên ở trạng thái chờ
            state.setAwaiting(
                    Awaiting.NONE
            );

            return fail(
                    context,
                    exception
            );
        }
    }

    // ------------------------------------------------------------------
    // Đặt lịch
    // ------------------------------------------------------------------

    private ChatResponse create(
            HandlerContext context,
            ConversationState state
    ) {

        if (context.principal()
                .getRole() != RoleCode.MEMBER) {

            return deniedRole(
                    context
            );
        }

        if (context.principal()
                .getMemberId() == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "booking.no_member"
                    )
            );
        }

        startIfNeeded(
                state
        );

        fillSlots(
                context,
                state
        );

        ServiceCode service =
                serviceSlot(
                        state
                );

        if (service == null) {
            return ask(
                    context,
                    state,
                    Awaiting.SERVICE,
                    "booking.ask_service"
            );
        }

        LocalDate date =
                dateSlot(
                        state
                );

        if (date == null) {
            return ask(
                    context,
                    state,
                    Awaiting.DATE,
                    "booking.ask_date"
            );
        }

        LocalTime time =
                timeSlot(
                        state
                );

        if (time == null) {
            return ask(
                    context,
                    state,
                    Awaiting.TIME,
                    "booking.ask_time"
            );
        }

        Long branchId =
                resolveBranch(
                        context,
                        state
                );

        if (branchId == null) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "info.no_data"
                    )
            );
        }

        FacilityResponse facility =
                facility(
                        context,
                        branchId,
                        service
                );

        if (facility == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "booking.no_facility",
                            Map.of(
                                    "branch",
                                    branchName(
                                            branchId
                                    ),
                                    "service",
                                    Fmt.service(
                                            service
                                    )
                            )
                    )
            );
        }

        List<AvailabilitySlotResponse> slots =
                bookingService.availability(
                        context.principal(),
                        facility.id(),
                        date
                );

        List<AvailabilitySlotResponse> free =
                free(
                        slots
                );

        if (free.isEmpty()) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "booking.none_available",
                            Map.of(
                                    "date",
                                    Fmt.date(
                                            date
                                    ),
                                    "branch",
                                    branchName(
                                            branchId
                                    ),
                                    "service",
                                    Fmt.service(
                                            service
                                    )
                            )
                    )
            );
        }

        AvailabilitySlotResponse chosen =
                free.stream()
                        .filter(slot ->
                                localTime(
                                        slot
                                )
                                        .equals(
                                                time
                                        )
                        )
                        .findFirst()
                        .orElse(null);

        if (chosen == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "booking.time_not_available",
                            Map.of(
                                    "time",
                                    Fmt.time(
                                            time
                                    ),
                                    "slots",
                                    slotLines(
                                            free
                                    )
                            )
                    )
            );
        }

        return confirmCreate(
                context,
                state,
                branchId,
                service,
                facility,
                chosen
                );
    }

    private ChatResponse confirmCreate(
            HandlerContext context,
            ConversationState state,
            Long branchId,
            ServiceCode service,
            FacilityResponse facility,
            AvailabilitySlotResponse slot
    ) {

        BranchResponse branch =
                branchService.get(
                        branchId
                );

        int duration =
                duration(
                        branchId,
                        service
                );

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "branchId",
                branchId
        );

        payload.put(
                "branchName",
                branch.name()
        );

        payload.put(
                "branchPhone",
                branch.phone()
        );

        payload.put(
                "serviceCode",
                service.name()
        );

        payload.put(
                "facilityId",
                facility.id()
        );

        payload.put(
                "facilityName",
                facility.name()
        );

        payload.put(
                "startsAt",
                slot.startsAtUtc()
                        .toString()
        );

        payload.put(
                "durationMinutes",
                duration
        );

        PendingAction pending =
                pending(
                        PendingAction.TYPE_BOOKING_CREATE,
                        payload
                );

        state.setPending(
                pending
        );

        state.setAwaiting(
                Awaiting.CONFIRM
        );

        state.setActiveIntent(
                Intent.BOOKING_CREATE
        );

        List<ChatCardLine> lines =
                List.of(
                        ChatCardLine.of(
                                "Dịch vụ",
                                Fmt.service(
                                        service
                                )
                        ),
                        ChatCardLine.of(
                                "Chi nhánh",
                                branch.name()
                        ),
                        ChatCardLine.of(
                                "Cơ sở",
                                facility.name()
                        ),
                        ChatCardLine.of(
                                "Thời gian",
                                Fmt.dateTime(
                                        slot.startsAtUtc(),
                                        TimeUtil.VIETNAM
                                )
                        ),
                        ChatCardLine.of(
                                "Thời lượng",
                                duration + " phút"
                        )
                );

        return IntentHandler.message(
                        context,
                        templates.get(
                                "booking.confirm"
                        )
                )
                .withCard(
                        ChatCard.confirm(
                                "Xác nhận đặt lịch",
                                lines,
                                confirmPayload(
                                        pending
                                ),
                                cancelPayload(
                                        pending
                                )
                        )
                )
                .withSuggestions(
                        List.of(
                                ChatSuggestion.payload(
                                        "Xác nhận",
                                        confirmPayload(
                                                pending
                                        )
                                ),
                                ChatSuggestion.payload(
                                        "Hủy",
                                        cancelPayload(
                                                pending
                                        )
                                )
                        )
                );
    }

    // ------------------------------------------------------------------
    // Hủy lịch
    // ------------------------------------------------------------------

    private ChatResponse cancel(
            HandlerContext context,
            ConversationState state
    ) {

        if (context.principal()
                .getRole() != RoleCode.MEMBER) {

            return deniedRole(
                    context
            );
        }

        if (context.principal()
                .getMemberId() == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "booking.no_member"
                    )
            );
        }

        startIfNeeded(
                state
        );

        fillSlots(
                context,
                state
        );

        List<BookingResponse> upcoming =
                upcoming(
                        context
                );

        if (upcoming.isEmpty()) {

            reset(
                    state
            );

            return IntentHandler.message(
                    context,
                    templates.get(
                            "cancel.none"
                    )
            );
        }

        Pick pick =
                pick(
                        context,
                        state,
                        upcoming
                );

        if (pick.booking() == null) {

            if (pick.reply() != null) {
                return pick.reply();
            }

            return askPick(
                    context,
                    state,
                    upcoming
            );
        }

        return confirmCancel(
                context,
                state,
                pick.booking()
                );
    }

    private ChatResponse askPick(
            HandlerContext context,
            ConversationState state,
            List<BookingResponse> upcoming
    ) {

        boolean askedBefore =
                state.slot(
                        SLOT_ASKED_PICK
                ) != null;

        state.put(
                SLOT_ASKED_PICK,
                "1"
        );

        state.setAwaiting(
                Awaiting.BOOKING_PICK
        );

        state.setActiveIntent(
                Intent.BOOKING_CANCEL
        );

        List<ChatCardLine> lines =
                upcoming.stream()
                        .map(booking ->
                                ChatCardLine.of(
                                        booking.bookingCode(),
                                        Fmt.service(
                                                booking.serviceCode()
                                        )
                                                + " — "
                                                + Fmt.dateTime(
                                                booking.startsAtUtc(),
                                                TimeUtil.VIETNAM
                                        )
                                ))
                        .toList();

        List<ChatSuggestion> suggestions =
                upcoming.stream()
                        .limit(4)
                        .map(booking ->
                                ChatSuggestion.payload(
                                        booking.bookingCode(),
                                        "TEXT:" + booking.bookingCode()
                                ))
                        .toList();

        return IntentHandler.message(
                        context,
                        templates.get(
                                askedBefore
                                        ? "booking.ask_pick_again"
                                        : "cancel.ask_pick"
                        ),
                        suggestions
                )
                .withCard(
                        ChatCard.list(
                                "Lịch sắp tới của bạn",
                                lines
                        )
                );
    }

    private ChatResponse confirmCancel(
            HandlerContext context,
            ConversationState state,
            BookingResponse booking
    ) {

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "bookingId",
                booking.id()
        );

        payload.put(
                "bookingCode",
                booking.bookingCode()
        );

        payload.put(
                "serviceCode",
                booking.serviceCode()
                .name()
        );

        payload.put(
                "startsAt",
                booking.startsAtUtc()
                .toString()
        );

        payload.put(
                "branchId",
                booking.branchId()
        );

        BranchResponse branch =
                branchService.get(
                        booking.branchId()
                );

        payload.put(
                "branchName",
                branch.name()
        );

        payload.put(
                "branchPhone",
                branch.phone()
        );

        PendingAction pending =
                pending(
                        PendingAction.TYPE_BOOKING_CANCEL,
                        payload
                );

        state.setPending(
                pending
        );

        state.setAwaiting(
                Awaiting.CONFIRM
        );

        state.setActiveIntent(
                Intent.BOOKING_CANCEL
        );

        List<ChatCardLine> lines =
                List.of(
                        ChatCardLine.of(
                                "Mã lịch",
                                booking.bookingCode()
                        ),
                        ChatCardLine.of(
                                "Dịch vụ",
                                Fmt.service(
                                        booking.serviceCode()
                                )
                        ),
                        ChatCardLine.of(
                                "Thời gian",
                                Fmt.dateTime(
                                        booking.startsAtUtc(),
                                        TimeUtil.VIETNAM
                                )
                        ),
                        ChatCardLine.of(
                                "Chi nhánh",
                                branch.name()
                        )
                );

        return IntentHandler.message(
                        context,
                        templates.get(
                                "cancel.confirm"
                        )
                )
                .withCard(
                        ChatCard.confirm(
                                "Xác nhận hủy lịch",
                                lines,
                                confirmPayload(
                                        pending
                                ),
                                cancelPayload(
                                        pending
                                )
                        )
                )
                .withSuggestions(
                        List.of(
                                ChatSuggestion.payload(
                                        "Xác nhận",
                                        confirmPayload(
                                                pending
                                        )
                                ),
                                ChatSuggestion.payload(
                                        "Giữ lịch",
                                        cancelPayload(
                                                pending
                                        )
                                )
                        )
                );
    }

    // ------------------------------------------------------------------
    // Xem khung giờ trống
    // ------------------------------------------------------------------

    private ChatResponse availability(
            HandlerContext context,
            ConversationState state
    ) {

        startIfNeeded(
                state
        );

        fillSlots(
                context,
                state
        );

        ServiceCode service =
                serviceSlot(
                        state
                );

        if (service == null) {
            return ask(
                    context,
                    state,
                    Awaiting.SERVICE,
                    "booking.ask_service"
            );
        }

        LocalDate date =
                dateSlot(
                        state
                );

        if (date == null) {
            return ask(
                    context,
                    state,
                    Awaiting.DATE,
                    "booking.ask_date"
            );
        }

        Long branchId =
                resolveBranch(
                        context,
                        state
                );

        if (branchId == null) {
            return IntentHandler.message(
                    context,
                    templates.get(
                            "info.no_data"
                    )
            );
        }

        FacilityResponse facility =
                facility(
                        context,
                        branchId,
                        service
                );

        if (facility == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "booking.no_facility",
                            Map.of(
                                    "branch",
                                    branchName(
                                            branchId
                                    ),
                                    "service",
                                    Fmt.service(
                                            service
                                    )
                            )
                    )
            );
        }

        List<AvailabilitySlotResponse> free =
                free(
                        bookingService.availability(
                                context.principal(),
                                facility.id(),
                                date
                        )
                );

        Map<String, Object> vars =
                Map.of(
                        "date",
                        Fmt.date(
                                date
                        ),
                        "branch",
                        branchName(
                                branchId
                        ),
                        "slots",
                        slotLines(
                                free
                        )
                );

        state.setAwaiting(
                Awaiting.NONE
        );

        state.setActiveIntent(
                Intent.BOOKING_AVAILABILITY
        );

        if (free.isEmpty()) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "availability.none",
                            vars
                    )
            );
        }

        return IntentHandler.message(
                context,
                templates.get(
                        "availability",
                        vars
                )
        );
    }

    // ------------------------------------------------------------------
    // Slot
    // ------------------------------------------------------------------

    /**
     * Lượt mới (không đang chờ gì) → bỏ slot cũ để câu trả lời trước không
     * làm sai lượt này.
     */
    private void startIfNeeded(
            ConversationState state
    ) {

        if (state.getAwaiting()
                == Awaiting.NONE) {

            state.getSlots()
                    .clear();

            state.setPending(
                    null
            );
        }
    }

    private void fillSlots(
            HandlerContext context,
            ConversationState state
    ) {

        var entities =
                context.entities();

        if (entities == null) {
            return;
        }

        if (entities.services()
                != null
                && !entities.services()
                .isEmpty()) {

            state.put(
                    SLOT_SERVICE,
                    entities.services()
                            .iterator()
                            .next()
                            .name()
            );
        }

        if (entities.date() != null) {
            state.put(
                    SLOT_DATE,
                    entities.date()
                            .toString()
            );
        }

        if (entities.time() != null) {
            state.put(
                    SLOT_TIME,
                    Fmt.time(
                            entities.time()
                    )
            );
        }

        if (entities.branchId() != null) {
            state.put(
                    SLOT_BRANCH,
                    String.valueOf(
                            entities.branchId()
                    )
            );
        }

        if (entities.bookingCode()
                != null) {
            state.put(
                    SLOT_BOOKING_CODE,
                    entities.bookingCode()
            );
        }
    }

    private ChatResponse ask(
            HandlerContext context,
            ConversationState state,
            Awaiting awaiting,
            String templateKey
    ) {

        state.setAwaiting(
                awaiting
        );

        state.setActiveIntent(
                context.intent()
        );

        return IntentHandler.message(
                context,
                templates.get(
                        templateKey
                )
        );
    }

    private ServiceCode serviceSlot(
            ConversationState state
    ) {

        String value =
                state.slot(
                        SLOT_SERVICE
                );

        if (value == null
                || value.isBlank()) {
            return null;
        }

        try {
            return ServiceCode.valueOf(
                    value
            );

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private LocalDate dateSlot(
            ConversationState state
    ) {

        String value =
                state.slot(
                        SLOT_DATE
                );

        if (value == null
                || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(
                    value
            );

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private LocalTime timeSlot(
            ConversationState state
    ) {

        String value =
                state.slot(
                        SLOT_TIME
                );

        if (value == null
                || value.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(
                    value
            );

        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Chi nhánh: nói rõ → lấy; không thì gói tập của hội viên; không thì
     * chi nhánh đầu tiên đang hoạt động.
     */
    private Long resolveBranch(
            HandlerContext context,
            ConversationState state
    ) {

        String slot =
                state.slot(
                        SLOT_BRANCH
                );

        if (slot != null
                && !slot.isBlank()) {
            return Long.valueOf(
                    slot
            );
        }

        Long branchId =
                null;

        if (context.principal()
                .getMemberId() != null) {

            try {

                MembershipResponse membership =
                        membershipService.current(
                                context.principal(),
                                context.principal()
                                        .getMemberId()
                        );

                if (membership != null) {
                    branchId =
                            membership.branchId();
                }

            } catch (ApiException exception) {

                // Chưa có gói → thử chi nhánh mặc định
                branchId =
                        null;
            }
        }

        if (branchId == null) {

            List<BranchResponse> branches =
                    branchService.list(
                            BranchStatus.ACTIVE
                    );

            if (branches.isEmpty()) {
                return null;
            }

            branchId =
                    branchSupport.defaultBranch(
                            context,
                            branches
                    )
                            .id();
        }

        state.put(
                SLOT_BRANCH,
                String.valueOf(
                        branchId
                )
        );

        return branchId;
    }

    private FacilityResponse facility(
            HandlerContext context,
            Long branchId,
            ServiceCode service
    ) {

        return facilityService.list(
                        context.principal(),
                        branchId,
                        service,
                        FacilityStatus.ACTIVE
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String branchName(
            Long branchId
    ) {
        try {

            return branchService.get(
                    branchId
            )
                    .name();

        } catch (ApiException exception) {
            return "chi nhánh #" + branchId;
        }
    }

    private int duration(
            Long branchId,
            ServiceCode service
    ) {

        try {

            return branchService.getServices(
                            branchId
                    )
                    .stream()
                    .filter(config ->
                            config.serviceCode()
                                    == service
                    )
                    .map(
                            BranchServiceResponse::bookingDurationMinutes
                    )
                    .filter(value ->
                            value != null
                                    && value > 0
                    )
                    .findFirst()
                    .orElse(60);

        } catch (ApiException exception) {
            return 60;
        }
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private List<BookingResponse> upcoming(
            HandlerContext context
    ) {

        Instant now =
                TimeUtil.now();

        return bookingService.list(
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
                        java.util.Comparator.comparing(
                                BookingResponse::startsAtUtc
                        )
                )
                .toList();
    }

    /**
     * Chọn lịch cần hủy: slot đã có → mã lịch trong câu → chỉ còn một lịch.
     *
     * @return {@code booking != null} nếu chọn được; {@code reply != null} nếu
     * người dùng nêu mã nhưng mã không có trong danh sách.
     */
    private Pick pick(
            HandlerContext context,
            ConversationState state,
            List<BookingResponse> upcoming
    ) {

        String slotId =
                state.slot(
                        SLOT_BOOKING_ID
                );

        if (slotId != null
                && !slotId.isBlank()) {

            BookingResponse found =
                    findById(
                            upcoming,
                            slotId
                    );

            if (found != null) {
                return new Pick(
                        found,
                        null
                );
            }
        }

        String code =
                state.slot(
                        SLOT_BOOKING_CODE
                ) != null
                        ? state.slot(
                        SLOT_BOOKING_CODE
                )
                        : context.entities() == null
                        ? null
                        : context.entities()
                        .bookingCode();

        if (code != null
                && !code.isBlank()) {

            BookingResponse found =
                    findByCode(
                            upcoming,
                            code
                    );

            if (found != null) {
                return new Pick(
                        found,
                        null
                );
            }

            return new Pick(
                    null,
                    IntentHandler.message(
                            context,
                            templates.get(
                                    "cancel.not_found",
                                    Map.of(
                                            "code",
                                            code
                                    )
                            )
                    )
            );
        }

        if (upcoming.size()
                == 1) {
            return new Pick(
                    upcoming.get(0),
                    null
            );
        }

        return new Pick(
                null,
                null
        );
    }

    private static BookingResponse findById(
            List<BookingResponse> bookings,
            String id
    ) {

        long value;

        try {
            value =
                    Long.parseLong(
                            id
                    );

        } catch (NumberFormatException exception) {
            return null;
        }

        return bookings.stream()
                .filter(booking ->
                        booking.id()
                                .equals(
                                        value
                                )
                )
                .findFirst()
                .orElse(null);
    }

    private static BookingResponse findByCode(
            List<BookingResponse> bookings,
            String code
    ) {
        return bookings.stream()
                .filter(booking ->
                        code.equalsIgnoreCase(
                                booking.bookingCode()
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private static List<AvailabilitySlotResponse> free(
            List<AvailabilitySlotResponse> slots
    ) {

        if (slots == null) {
            return List.of();
        }

        return slots.stream()
                .filter(slot ->
                        slot.remainingCapacity()
                                > 0
                )
                .sorted(
                        java.util.Comparator.comparing(
                                AvailabilitySlotResponse::startsAtUtc
                        )
                )
                .toList();
    }

    private static LocalTime localTime(
            AvailabilitySlotResponse slot
    ) {
        return slot.startsAtUtc()
                .atZone(
                        TimeUtil.VIETNAM
                )
                .toLocalTime();
    }

    private String slotLines(
            List<AvailabilitySlotResponse> slots
    ) {

        List<String> lines =
                new ArrayList<>();

        for (AvailabilitySlotResponse slot : slots) {

            lines.add(
                    "• "
                            + Fmt.time(
                            slot.startsAtUtc(),
                            TimeUtil.VIETNAM
                    )
                            + " — còn "
                            + slot.remainingCapacity()
                            + " chỗ"
            );
        }

        return String.join(
                "\n",
                lines
        );
    }

    private PendingAction pending(
            String type,
            Map<String, Object> payload
    ) {

        String json;

        try {

            json = objectMapper.writeValueAsString(
                    payload
            );

        } catch (Exception exception) {

            log.warn(
                    "Không serialize được payload {}: {}",
                    type,
                    exception.getMessage()
            );

            json = "{}";
        }

        return PendingAction.of(
                type,
                json,
                TimeUtil.now()
                        .plusSeconds(
                                Math.max(
                                        1,
                                        pendingTtlMinutes
                                )
                                * 60L
                        )
        );
    }

    private static String confirmPayload(
            PendingAction pending
    ) {
        return "CONFIRM:"
                + pending.id();
    }

    private static String cancelPayload(
            PendingAction pending
    ) {
        return "CANCEL:"
                + pending.id();
    }

    private void reset(
            ConversationState state
    ) {
        state.setAwaiting(
                Awaiting.NONE
        );

        state.setPending(
                null
        );

        state.setActiveIntent(
                null
        );

        state.getSlots()
                .clear();
    }

    private ChatResponse deniedRole(
            HandlerContext context
    ) {

        return IntentHandler.message(
                context,
                templates.get(
                        "denied.role",
                        Map.of(
                                "allowed",
                                "xem gói tập, lịch của bạn, FAQ và thông tin chi nhánh"
                        )
                )
        );
    }

    /** Kết quả chọn lịch để hủy. */
    private record Pick(
            BookingResponse booking,
            ChatResponse reply
    ) {
    }
}
