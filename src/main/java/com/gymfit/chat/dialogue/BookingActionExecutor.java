package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.booking.BookingService;
import com.gymfit.booking.dto.BookingCancelRequest;
import com.gymfit.booking.dto.BookingCreateRequest;
import com.gymfit.branch.ServiceCode;
import com.gymfit.chat.dto.ChatCardLine;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Thực thi {@link PendingAction} đã được người dùng xác nhận.
 *
 * <p>Đây là nơi <b>duy nhất</b> chatbot được phép ghi dữ liệu: mọi lỗi đều trả
 * câu tiếng Việt, không bao giờ lộ stack trace, và trạng thái hội thoại được
 * dọn sau mỗi lần thực thi (dù thành công hay thất bại) để không lặp lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingActionExecutor {

    private final BookingService bookingService;

    private final ResponseTemplates templates;

    private final ObjectMapper objectMapper;

    /**
     * Thực thi hành động chờ xác nhận.
     *
     * @return câu trả lời đã thành công / thất bại (một trong hai, không ném)
     */
    public ChatResponse execute(
            AppPrincipal principal,
            PendingAction pending
    ) {

        JsonNode payload =
                null;

        try {

            payload =
                    objectMapper.readTree(
                            pending.payloadJson()
                    );

            return switch (pending.type()) {
                case PendingAction.TYPE_BOOKING_CREATE ->
                        create(
                                principal,
                                payload
                        );

                case PendingAction.TYPE_BOOKING_CANCEL ->
                        cancel(
                                principal,
                                payload
                        );

                default ->
                        response(
                                templates.get(
                                        "pending.invalid"
                                )
                        );
            };

        } catch (ApiException exception) {

            log.warn(
                    "Thực thi {} thất bại: {} - {}",
                    pending.type(),
                    exception.getCode(),
                    exception.getMessage()
            );

            return response(
                    message(
                            exception,
                            payload
                    )
            );

        } catch (Exception exception) {

            log.error(
                    "Lỗi không mong đợi khi thực thi {}: {}",
                    pending.type(),
                    exception.getMessage(),
                    exception
            );

            return response(
                    templates.get(
                            "error.generic"
                    )
            );
        }
    }

    // ------------------------------------------------------------------

    private ChatResponse create(
            AppPrincipal principal,
            JsonNode payload
    ) {

        BookingCreateRequest request =
                new BookingCreateRequest(
                        null,
                        payload.path("branchId")
                                .asLong(),
                        ServiceCode.valueOf(
                                payload.path("serviceCode")
                                        .asText()
                        ),
                        payload.path("facilityId")
                                .asLong(),
                        Instant.parse(
                                payload.path("startsAt")
                                        .asText()
                        )
                );

        BookingResponse booking =
                bookingService.create(
                        principal,
                        request
                );

        return response(
                templates.get(
                        "booking.done",
                        Map.of(
                                "code",
                                booking.bookingCode(),
                                "service",
                                Fmt.service(
                                        booking.serviceCode()
                                ),
                                "branch",
                                text(
                                        payload,
                                        "branchName"
                                ),
                                "facility",
                                text(
                                        payload,
                                        "facilityName"
                                ),
                                "time",
                                Fmt.dateTime(
                                        booking.startsAtUtc(),
                                        TimeUtil.VIETNAM
                                ),
                                "duration",
                                payload.path("durationMinutes")
                                        .asInt(60)
                        )
                )
        );
    }

    private ChatResponse cancel(
            AppPrincipal principal,
            JsonNode payload
    ) {

        long bookingId =
                payload.path("bookingId")
                        .asLong();

        BookingResponse booking =
                bookingService.cancel(
                        principal,
                        bookingId,
                        new BookingCancelRequest(
                                "Hủy qua trợ lý GYMFIT"
                        )
                );

        return response(
                templates.get(
                        "cancel.done",
                        Map.of(
                                "code",
                                booking.bookingCode()
                        )
                )
        );
    }

    // ------------------------------------------------------------------

    private String message(
            ApiException exception,
            JsonNode payload
    ) {

        if ("booking_cancellation_too_late"
                .equals(
                        exception.getCode()
                )) {

            // Câu chung có sẵn trong template, chỉ thiếu số điện thoại chi nhánh
            String phone =
                    payload == null
                            ? ""
                            : text(
                            payload,
                            "branchPhone"
                    );

            return templates.get(
                    "cancel.too_late",
                    Map.of(
                            "phone",
                            phone.isBlank()
                                    ? "0900 0000 00"
                                    : phone
                    )
            );
        }

        return templates.fromException(
                exception
        );
    }

    private static String text(
            JsonNode payload,
            String field
    ) {
        return payload.path(field)
                .asText("");
    }

    private static ChatResponse response(
            String message
    ) {
        return ChatResponse.of(
                null,
                message,
                null,
                null
        );
    }
}
