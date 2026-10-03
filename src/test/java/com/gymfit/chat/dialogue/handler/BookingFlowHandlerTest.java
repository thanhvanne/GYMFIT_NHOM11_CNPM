package com.gymfit.chat.dialogue.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.BookingStatus;
import com.gymfit.booking.dto.AvailabilitySlotResponse;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.FacilityService;
import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.FacilityType;
import com.gymfit.branch.ServiceCode;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.branch.dto.BranchServiceResponse;
import com.gymfit.branch.dto.FacilityResponse;
import com.gymfit.chat.dialogue.Awaiting;
import com.gymfit.chat.dialogue.ConversationState;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlu.DateTimeParser;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlu.NormalizedText;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.chat.nlu.entity.EntityExtractor;
import com.gymfit.chat.nlu.entity.GazetteerProvider;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.membership.MembershipService;
import com.gymfit.membership.dto.MembershipResponse;
import com.gymfit.user.AppUser;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Luồng đặt / hủy lịch — mock service, không cần DB.
 */
class BookingFlowHandlerTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 3);

    private static final LocalDate TOMORROW =
            TODAY.plusDays(1);

    private BookingService bookingService;
    private FacilityService facilityService;
    private BranchService branchService;
    private MembershipService membershipService;
    private ResponseTemplates templates;
    private BookingFlowHandler handler;

    private TextNormalizer normalizer;
    private EntityExtractor extractor;

    private ConversationState state;

    @BeforeEach
    void setUp() {

        bookingService =
                mock(BookingService.class);

        facilityService =
                mock(FacilityService.class);

        branchService =
                mock(BranchService.class);

        membershipService =
                mock(MembershipService.class);

        templates =
                new ResponseTemplates();

        templates.load();

        normalizer =
                new TextNormalizer();

        normalizer.load();

        extractor =
                new EntityExtractor(
                        new GazetteerProvider() {
                            @Override
                            public Map<String, Long> branchAliases() {
                                return Map.of(
                                        "q1", 1L,
                                        "q7", 2L
                                );
                            }

                            @Override
                            public Map<String, String> productAliases() {
                                return Map.of();
                            }
                        },
                        new DateTimeParser()
                );

        extractor.load();

        handler =
                new BookingFlowHandler(
                        bookingService,
                        facilityService,
                        branchService,
                        membershipService,
                        templates,
                        new BranchSupport(
                                membershipService
                        ),
                        new ObjectMapper()
                );

        state =
                new ConversationState();

        // Chi nhánh mặc định qua gói tập của hội viên
        when(membershipService.current(
                any(),
                eq(1L)
        )).thenReturn(
                new MembershipResponse(
                        1L,
                        1L,
                        2L,
                        3L,
                        1L,
                        com.gymfit.membership.MembershipStatus.ACTIVE,
                        LocalDate.of(
                                2026,
                                9,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                12,
                                1
                        ),
                        Instant.now(),
                        null,
                        null,
                        Set.of(ServiceCode.GYM),
                        Instant.now()
                )
        );

        when(branchService.get(
                1L
        )).thenReturn(
                branch(
                        1L,
                        "Q1",
                        "Quận 1"
                )
        );

        when(branchService.getServices(
                1L
        )).thenReturn(
                List.of(
                        new BranchServiceResponse(
                                1L,
                                ServiceCode.GYM,
                                60,
                                20,
                                true
                        )
                )
        );
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private static BranchResponse branch(
            Long id,
            String code,
            String name
    ) {
        return new BranchResponse(
                id,
                code,
                name,
                "Địa chỉ " + code,
                "0900000000",
                BranchStatus.ACTIVE,
                "Asia/Ho_Chi_Minh",
                Instant.now(),
                Instant.now()
        );
    }

    private static FacilityResponse facility() {
        return new FacilityResponse(
                10L,
                1L,
                ServiceCode.GYM,
                FacilityType.GYM_AREA,
                "Khu máy chạy bộ",
                20,
                FacilityStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
    }

    private AppPrincipal member() {

        AppUser user =
                AppUser.builder()
                        .id(1L)
                        .fullName("Nguyễn Văn A")
                        .email("a@gymfit.local")
                        .passwordHash("x")
                        .roleCode(RoleCode.MEMBER)
                        .status(UserStatus.ACTIVE)
                        .memberId(1L)
                        .build();

        return new AppPrincipal(
                user
        );
    }

    private HandlerContext context(
            AppPrincipal principal,
            Intent intent,
            String raw
    ) {

        NormalizedText text =
                normalizer.normalize(raw);

        Entities entities =
                extractor.extract(
                        text,
                        TODAY
                );

        return new HandlerContext(
                principal,
                intent,
                entities,
                text,
                raw,
                TODAY,
                state.getSlots(),
                state
        );
    }

    private static Instant targetInstant(
            LocalTime time
    ) {
        return TOMORROW.atTime(time)
                .atZone(TimeUtil.VIETNAM)
                .toInstant();
    }

    private void withFacilityAndSlots(
            AvailabilitySlotResponse... slots
    ) {

        when(facilityService.list(
                any(),
                eq(1L),
                eq(ServiceCode.GYM),
                eq(FacilityStatus.ACTIVE)
        )).thenReturn(
                List.of(
                        facility()
                )
        );

        when(bookingService.availability(
                any(),
                eq(10L),
                eq(TOMORROW)
        )).thenReturn(
                List.of(
                        slots
                )
        );
    }

    // ==================================================================
    // Đặt lịch
    // ==================================================================

    @Test
    @DisplayName("Đặt lịch đủ slot → thẻ Xác nhận, KHÔNG gọi BookingService.create")
    void createPlansButDoesNotWrite() {

        withFacilityAndSlots(
                new AvailabilitySlotResponse(
                        targetInstant(
                                LocalTime.of(
                                        19,
                                        0
                                )
                        ),
                        targetInstant(
                                LocalTime.of(
                                        20,
                                        0
                                )
                        ),
                        3
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CREATE,
                                "đặt lịch gym lúc 19:00 ngày mai"
                        )
                );

        assertEquals(
                templates.get(
                        "booking.confirm"
                ),
                response.message()
        );

        assertNotNull(
                response.card()
        );

        assertEquals(
                "CONFIRM",
                response.card()
                        .type()
        );

        assertTrue(
                response.card()
                        .confirmPayload()
                        .startsWith(
                                "CONFIRM:"
                        )
        );

        assertEquals(
                Awaiting.CONFIRM,
                state.getAwaiting()
        );

        assertNotNull(
                state.getPending()
        );

        assertEquals(
                "BOOKING_CREATE",
                state.getPending()
                        .type()
        );
    }

    @Test
    @DisplayName("Thiếu giờ → hỏi khung giờ, slotservice vẫn còn lần sau")
    void createAsksForMissingTime() {

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CREATE,
                                "đặt lịch gym ngày mai"
                        )
                );

        assertEquals(
                templates.get(
                        "booking.ask_time"
                ),
                response.message()
        );

        assertEquals(
                Awaiting.TIME,
                state.getAwaiting()
        );

        // Chưa có gì được ghi
        org.mockito.Mockito.verify(
                bookingService,
                org.mockito.Mockito.never()
        ).create(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Người dùng nói tiếp giờ →slot đầy đủ thì ra thẻ xác nhận")
    void createFillsSlotAcrossTurns() {

        handler.handle(
                context(
                        member(),
                        Intent.BOOKING_CREATE,
                        "đặt lịch gym ngày mai"
                )
        );

        withFacilityAndSlots(
                new AvailabilitySlotResponse(
                        targetInstant(
                                LocalTime.of(
                                        19,
                                        0
                                )
                        ),
                        targetInstant(
                                LocalTime.of(
                                        20,
                                        0
                                )
                        ),
                        5
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CREATE,
                                "19:00"
                        )
                );

        assertEquals(
                Awaiting.CONFIRM,
                state.getAwaiting()
        );

        assertEquals(
                templates.get(
                        "booking.confirm"
                ),
                response.message()
        );
    }

    @Test
    @DisplayName("Khung giờ đã kín → báo không còn chỗ, không hỏi xác nhận")
    void createWhenSlotFull() {

        withFacilityAndSlots(
                new AvailabilitySlotResponse(
                        targetInstant(
                                LocalTime.of(
                                        19,
                                        0
                                )
                        ),
                        targetInstant(
                                LocalTime.of(
                                        20,
                                        0
                                )
                        ),
                        0
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CREATE,
                                "đặt lịch gym lúc 19:00 ngày mai"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "không còn khung giờ trống"
                        ),
                response.message()
        );

        assertNull(
                state.getPending()
        );
    }

    @Test
    @DisplayName("Giờ người dùng chọn chưa có → liệt kê khung giờ còn trống")
    void createWhenTimeNotOnSchedule() {

        withFacilityAndSlots(
                new AvailabilitySlotResponse(
                        targetInstant(
                                LocalTime.of(
                                        21,
                                        0
                                )
                        ),
                        targetInstant(
                                LocalTime.of(
                                        22,
                                        0
                                )
                        ),
                        4
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CREATE,
                                "đặt lịch gym lúc 19:00 ngày mai"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "đã kín"
                        ),
                response.message()
        );

        assertTrue(
                response.message()
                        .contains(
                                "21:00"
                        ),
                response.message()
        );

        assertNull(
                state.getPending()
        );
    }

    @Test
    @DisplayName("Hội viên asks dịch vụ chi nhánh không có → báo rõ, không hỏi xác nhận")
    void createWithoutFacility() {

        when(facilityService.list(
                any(),
                eq(1L),
                eq(ServiceCode.GYM),
                eq(FacilityStatus.ACTIVE)
        )).thenReturn(
                List.of()
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CREATE,
                                "đặt lịch gym lúc 19:00 ngày mai"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "chưa có cơ sở vật chất"
                        ),
                response.message()
        );

        assertNull(
                state.getPending()
        );
    }

    @Test
    @DisplayName("Nhân viên không được đặt lịch qua chatbot")
    void createDeniedForStaff() {

        AppUser user =
                AppUser.builder()
                        .id(2L)
                        .fullName("Quản lý")
                        .email("m@gymfit.local")
                        .passwordHash("x")
                        .roleCode(RoleCode.BRANCH_MANAGER)
                        .status(UserStatus.ACTIVE)
                        .branchId(1L)
                        .build();

        ChatResponse response =
                handler.handle(
                        context(
                                new AppPrincipal(
                                        user
                                ),
                                Intent.BOOKING_CREATE,
                                "đặt lịch gym"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "không có quyền"
                        ),
                response.message()
        );

        org.mockito.Mockito.verify(
                bookingService,
                org.mockito.Mockito.never()
        ).create(
                any(),
                any()
        );
    }

    // ==================================================================
    // Hủy lịch
    // ==================================================================

    @Test
    @DisplayName("Không có lịch → 'chưa có lịch sắp tới để hủy'")
    void cancelWithoutUpcoming() {

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of()
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CANCEL,
                                "hủy lịch của tôi"
                        )
                );

        assertEquals(
                templates.get(
                        "cancel.none"
                ),
                response.message()
        );

        assertEquals(
                Awaiting.NONE,
                state.getAwaiting()
        );
    }

    @Test
    @DisplayName("Đúng một lịch → hỏi xác nhận ngay, chưa hủy")
    void cancelSingleBooking() {

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of(
                        booking(
                                5L,
                                "BOOK_1234567890ABCDEF",
                                BookingStatus.CONFIRMED,
                                TimeUtil.now()
                                        .plusSeconds(
                                                86_400
                                        )
                        )
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CANCEL,
                                "hủy lịch của tôi"
                        )
                );

        assertEquals(
                templates.get(
                        "cancel.confirm"
                ),
                response.message()
        );

        assertNotNull(
                state.getPending()
        );

        assertEquals(
                "BOOKING_CANCEL",
                state.getPending()
                        .type()
        );

        assertTrue(
                response.card()
                        .confirmPayload()
                        .startsWith(
                                "CONFIRM:"
                        )
        );

        org.mockito.Mockito.verify(
                bookingService,
                org.mockito.Mockito.never()
        ).cancel(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Nhiều lịch → hỏi chọn, liệt kê mã lịch trong thẻ")
    void cancelAsksToPick() {

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of(
                        booking(
                                5L,
                                "BOOK_1234567890ABCDEF",
                                BookingStatus.CONFIRMED,
                                TimeUtil.now()
                                        .plusSeconds(
                                                86_400
                                        )
                        ),
                        booking(
                                6L,
                                "BOOK_ABCDEF0123456789",
                                BookingStatus.CONFIRMED,
                                TimeUtil.now()
                                        .plusSeconds(
                                                172_800
                                        )
                        )
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CANCEL,
                                "hủy lịch của tôi"
                        )
                );

        assertEquals(
                Awaiting.BOOKING_PICK,
                state.getAwaiting()
        );

        assertEquals(
                "LIST",
                response.card()
                        .type()
        );

        assertTrue(
                response.card()
                        .lines()
                        .stream()
                        .anyMatch(
                                line ->
                                        line.label()
                                                .equals(
                                                        "BOOK_1234567890ABCDEF"
                                                )
                        )
        );

        assertNull(
                state.getPending()
        );
    }

    @Test
    @DisplayName("Nói mã lịch không có trong danh sách → báo không thấy, không bịa")
    void cancelUnknownCode() {

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of(
                        booking(
                                5L,
                                "BOOK_1234567890ABCDEF",
                                BookingStatus.CONFIRMED,
                                TimeUtil.now()
                                        .plusSeconds(
                                                86_400
                                        )
                        ),
                        booking(
                                6L,
                                "BOOK_ABCDEF0123456789",
                                BookingStatus.CONFIRMED,
                                TimeUtil.now()
                                        .plusSeconds(
                                                172_800
                                        )
                        )
                )
        );

        // Lượt chọn lịch: người dùng đọc mã
        state.setAwaiting(
                Awaiting.BOOKING_PICK
        );

        state.setActiveIntent(
                Intent.BOOKING_CANCEL
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CANCEL,
                                "hủy lịch BOOK_9999999999999999"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "BOOK_9999999999999999"
                        ),
                response.message()
        );

        assertNull(
                state.getPending()
        );

        org.mockito.Mockito.verify(
                bookingService,
                org.mockito.Mockito.never()
        ).cancel(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Chỉ còn một lịch sau khi lọc → tự chọn, hỏi xác nhận")
    void cancelPicksWhenOnlyOneLeft() {

        when(bookingService.list(
                any()
        )).thenReturn(
                List.of(
                        booking(
                                5L,
                                "BOOK_1234567890ABCDEF",
                                BookingStatus.CONFIRMED,
                                TimeUtil.now()
                                        .plusSeconds(
                                                86_400
                                        )
                        )
                )
        );

        state.setAwaiting(
                Awaiting.BOOKING_PICK
        );

        state.setActiveIntent(
                Intent.BOOKING_CANCEL
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_CANCEL,
                                "hủy lịch BOOK_1234567890ABCDEF"
                        )
                );

        assertEquals(
                templates.get(
                        "cancel.confirm"
                ),
                response.message()
        );

        assertNotNull(
                state.getPending()
        );
    }

    // ==================================================================
    // Xem khung giờ
    // ==================================================================

    @Test
    @DisplayName("Xem khung giờ: liệt kê giờ + số chỗ còn lại")
    void availabilityListsSlots() {

        withFacilityAndSlots(
                new AvailabilitySlotResponse(
                        targetInstant(
                                LocalTime.of(
                                        19,
                                        0
                                )
                        ),
                        targetInstant(
                                LocalTime.of(
                                        20,
                                        0
                                )
                        ),
                        3
                ),
                new AvailabilitySlotResponse(
                        targetInstant(
                                LocalTime.of(
                                        21,
                                        0
                                )
                        ),
                        targetInstant(
                                LocalTime.of(
                                        22,
                                        0
                                )
                        ),
                        0
                )
        );

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_AVAILABILITY,
                                "khung giờ trống gym ngày mai"
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "19:00"
                        ),
                response.message()
        );

        assertTrue(
                response.message()
                        .contains(
                                "còn 3 chỗ"
                        ),
                response.message()
        );

        assertFalse(
                response.message()
                        .contains(
                                "21:00"
                        ),
                "Khung giờ đã kín không được liệt kê: "
                        + response.message()
        );
    }

    @Test
    @DisplayName("Xem khung giờ thiếu ngày → hỏi ngày")
    void availabilityAsksDate() {

        ChatResponse response =
                handler.handle(
                        context(
                                member(),
                                Intent.BOOKING_AVAILABILITY,
                                "khung giờ trống gym"
                        )
                );

        assertEquals(
                templates.get(
                        "booking.ask_date"
                ),
                response.message()
        );

        assertEquals(
                Awaiting.DATE,
                state.getAwaiting()
        );
    }

    private static BookingResponse booking(
            Long id,
            String code,
            BookingStatus status,
            Instant startsAt
    ) {
        return new BookingResponse(
                id,
                code,
                1L,
                1L,
                1L,
                ServiceCode.GYM,
                10L,
                status,
                startsAt,
                startsAt.plusSeconds(
                        3600
                ),
                null,
                null,
                1L,
                Instant.now()
        );
    }
}
