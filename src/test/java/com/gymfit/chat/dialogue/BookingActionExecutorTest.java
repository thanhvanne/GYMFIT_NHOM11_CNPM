package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.dto.BookingCreateRequest;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.branch.ServiceCode;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.user.AppUser;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thao tác ghi dữ liệu — nơi DUY NHẤT chatbot được gọi BookingService.
 */
class BookingActionExecutorTest {

    private BookingService bookingService;
    private ResponseTemplates templates;
    private BookingActionExecutor executor;

    private AppPrincipal principal;

    @BeforeEach
    void setUp() {

        bookingService =
                mock(BookingService.class);

        templates =
                new ResponseTemplates();

        templates.load();

        executor =
                new BookingActionExecutor(
                        bookingService,
                        templates,
                        new ObjectMapper()
                );

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

        principal =
                new AppPrincipal(
                        user
                );
    }

    // ------------------------------------------------------------------

    private static String payload(
            Map<String, Object> values
    ) {

        try {
            return new ObjectMapper()
                    .writeValueAsString(
                            values
                    );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    exception
            );
        }
    }

    private static Map<String, Object> createPayload() {

        Map<String, Object> values =
                new LinkedHashMap<>();

        Instant startsAt =
                TimeUtil.now()
                        .plusSeconds(
                                86_400
                        );

        values.put(
                "branchId",
                1L
        );

        values.put(
                "branchName",
                "Quận 1"
        );

        values.put(
                "branchPhone",
                "0900000001"
        );

        values.put(
                "serviceCode",
                "GYM"
        );

        values.put(
                "facilityId",
                10L
        );

        values.put(
                "facilityName",
                "Khu máy chạy bộ"
        );

        values.put(
                "startsAt",
                startsAt.toString()
        );

        values.put(
                "durationMinutes",
                60
        );

        return values;
    }

    private static BookingResponse booking(
            String code
    ) {
        return new BookingResponse(
                5L,
                code,
                1L,
                1L,
                1L,
                ServiceCode.GYM,
                10L,
                com.gymfit.booking.BookingStatus.CONFIRMED,
                TimeUtil.now()
                        .plusSeconds(
                                86_400
                        ),
                TimeUtil.now()
                        .plusSeconds(
                                90_000
                        ),
                null,
                null,
                1L,
                Instant.now()
        );
    }

    // ==================================================================

    @Test
    @DisplayName("Xác nhận đặt lịch → gọi đúng BookingService.create với dữ liệu từ payload")
    void createCallsService() {

        when(bookingService.create(
                any(),
                any()
        )).thenReturn(
                booking(
                        "BOOK_1234567890ABCDEF"
                )
        );

        PendingAction pending =
                PendingAction.of(
                        PendingAction.TYPE_BOOKING_CREATE,
                        payload(
                                createPayload()
                        ),
                        TimeUtil.now()
                                .plusSeconds(
                                        300
                                )
                );

        ChatResponse response =
                executor.execute(
                        principal,
                        pending
                );

        ArgumentCaptor<BookingCreateRequest> captor =
                ArgumentCaptor.forClass(
                        BookingCreateRequest.class
                );

        verify(
                bookingService
        ).create(
                eq(principal),
                captor.capture()
        );

        BookingCreateRequest request =
                captor.getValue();

        assertEquals(
                1L,
                request.branchId()
        );

        assertEquals(
                ServiceCode.GYM,
                request.serviceCode()
        );

        assertEquals(
                10L,
                request.facilityId()
        );

        assertTrue(
                response.message()
                        .contains(
                                "Đã đặt lịch thành công"
                        ),
                response.message()
        );

        assertTrue(
                response.message()
                        .contains(
                                "BOOK_1234567890ABCDEF"
                        )
        );

        assertTrue(
                response.message()
                        .contains(
                                "Quận 1"
                        )
        );
    }

    @Test
    @DisplayName("Xác nhận hủy lịch → gọi BookingService.cancel với lý do rõ ràng")
    void cancelCallsService() {

        when(bookingService.cancel(
                any(),
                anyLong(),
                any()
        )).thenReturn(
                booking(
                        "BOOK_1234567890ABCDEF"
                )
        );

        Map<String, Object> values =
                createPayload();

        values.put(
                "bookingId",
                5L
        );

        values.put(
                "bookingCode",
                "BOOK_1234567890ABCDEF"
        );

        ChatResponse response =
                executor.execute(
                        principal,
                        new PendingAction(
                                "id-1",
                                PendingAction.TYPE_BOOKING_CANCEL,
                                payload(
                                        values
                                ),
                                TimeUtil.now()
                                        .plusSeconds(
                                                300
                                        )
                        )
                );

        verify(
                bookingService
        ).cancel(
                eq(principal),
                eq(5L),
                any()
        );

        assertTrue(
                response.message()
                        .contains(
                                "Đã hủy lịch thành công"
                        ),
                response.message()
        );
    }

    @Test
    @DisplayName("Hủy trễ quá 2 giờ → câu của template + số điện thoại chi nhánh, không stack trace")
    void cancelTooLateUsesTemplate() {

        when(bookingService.cancel(
                any(),
                anyLong(),
                any()
        )).thenThrow(
                new ConflictException(
                        "booking_cancellation_too_late",
                        "Chỉ được hủy lịch trước ít nhất 2 giờ"
                )
        );

        Map<String, Object> values =
                createPayload();

        values.put(
                "bookingId",
                5L
        );

        ChatResponse response =
                executor.execute(
                        principal,
                        new PendingAction(
                                "id-2",
                                PendingAction.TYPE_BOOKING_CANCEL,
                                payload(
                                        values
                                ),
                                TimeUtil.now()
                                        .plusSeconds(
                                                300
                                        )
                        )
                );

        assertTrue(
                response.message()
                        .contains(
                                "2 giờ"
                        ),
                response.message()
        );

        assertTrue(
                response.message()
                        .contains(
                                "0900000001"
                        ),
                response.message()
        );

        assertFalse(
                response.message()
                        .contains(
                                "Exception"
                        )
        );
    }

    @Test
    @DisplayName("Lỗi khác từ service → câu tiếng Việt của service")
    void otherApiExceptionBecomesMessage() {

        when(bookingService.create(
                any(),
                any()
        )).thenThrow(
                new ConflictException(
                        "booking_full",
                        "Khung giờ đã kín"
                )
        );

        ChatResponse response =
                executor.execute(
                        principal,
                        new PendingAction(
                                "id-3",
                                PendingAction.TYPE_BOOKING_CREATE,
                                payload(
                                        createPayload()
                                ),
                                TimeUtil.now()
                                        .plusSeconds(
                                                300
                                        )
                        )
                );

        assertEquals(
                "Khung giờ đã kín",
                response.message()
        );
    }

    @Test
    @DisplayName("Lỗi không mong đợi → câu chung, không lộ chi tiết kỹ thuật")
    void unexpectedErrorIsGeneric() {

        when(bookingService.create(
                any(),
                any()
        )).thenThrow(
                new IllegalStateException(
                        "chia sẻ kết nối DB"
                )
        );

        ChatResponse response =
                executor.execute(
                        principal,
                        new PendingAction(
                                "id-4",
                                PendingAction.TYPE_BOOKING_CREATE,
                                payload(
                                        createPayload()
                                ),
                                TimeUtil.now()
                                        .plusSeconds(
                                                300
                                        )
                        )
                );

        assertEquals(
                templates.get(
                        "error.generic"
                ),
                response.message()
        );

        assertFalse(
                response.message()
                        .contains(
                                "chia sẻ kết nối"
                        )
        );
    }

    @Test
    @DisplayName("Loại hành động lạ → câu 'yêu cầu xác nhận không hợp lệ'")
    void unknownTypeRejected() {

        ChatResponse response =
                executor.execute(
                        principal,
                        new PendingAction(
                                "id-5",
                                "DELETE_EVERYTHING",
                                "{}",
                                TimeUtil.now()
                                        .plusSeconds(
                                                300
                                        )
                        )
                );

        assertEquals(
                templates.get(
                        "pending.invalid"
                ),
                response.message()
        );
    }

    @Test
    @DisplayName("Payload hỏng → câu chung, không ném lỗi ra ngoài")
    void brokenPayloadIsGeneric() {

        ChatResponse response =
                executor.execute(
                        principal,
                        new PendingAction(
                                "id-6",
                                PendingAction.TYPE_BOOKING_CREATE,
                                "không phải json",
                                TimeUtil.now()
                                        .plusSeconds(
                                                300
                                        )
                        )
                );

        assertEquals(
                templates.get(
                        "error.generic"
                ),
                response.message()
        );
    }

    @Test
    @DisplayName("ApiException không có message → dùng câu error.generic")
    void apiExceptionWithoutMessage() {

        ApiException exception =
                new ApiException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "boom",
                        " "
                );

        assertTrue(
                templates.fromException(
                                exception
                        )
                        .contains(
                                "hệ thống đang bận"
                        )
        );
    }
}
