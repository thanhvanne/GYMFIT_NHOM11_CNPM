package com.gymfit.booking;

import com.gymfit.branch.BranchOperatingHour;
import com.gymfit.branch.BranchOperatingHourJdbcRepository;
import com.gymfit.branch.Facility;
import com.gymfit.branch.FacilityRepository;
import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.ServiceCode;
import com.gymfit.booking.dto.AvailabilitySlotResponse;
import com.gymfit.booking.dto.BookingCreateRequest;
import com.gymfit.booking.dto.BookingResponse;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khung giờ đặt lịch.
 *
 * <p>Regression chính: Hibernate đọc cột {@code time} của SQL Server bị lệch
 * múi giờ (06:00 trong DB → 14:00 khi đọc qua JPA) khiến {@code open > close},
 * không sinh được khung giờ nào và mọi lệnh đặt lịch bị chê "ngoài giờ hoạt
 * động". Sau khi chuyển sang đọc qua JDBC, giờ phải khớp với bảng
 * {@code branch_operating_hour}.
 */
@SpringBootTest
class BookingAvailabilityTest {

    private static final Long BRANCH_ID =
            1L;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private BranchOperatingHourJdbcRepository operatingHours;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AppUserRepository userRepository;

    private Facility facility;

    private AppPrincipal admin;

    private AppPrincipal member;

    @BeforeEach
    void setUp() {

        facility = facilityRepository
                .findAllByBranchIdAndServiceCodeAndStatusOrderByNameAsc(
                        BRANCH_ID,
                        ServiceCode.GYM,
                        FacilityStatus.ACTIVE
                )
                .stream()
                .findFirst()
                .orElseThrow();

        admin = principal(
                "admin@gymfit.local"
        );

        member = principal(
                "member1@gymfit.local"
        );
    }

    private AppPrincipal principal(
            String email
    ) {
        AppUser user = userRepository
                .findByEmailIgnoreCase(
                        email
                )
                .orElseThrow();

        return new AppPrincipal(
                user
        );
    }

    /** Thứ hai kế tiếp — luôn nằm trong tương lai nên mọi khung giờ đều mở. */
    private static LocalDate nextMonday() {
        return TimeUtil.today()
                .with(
                        TemporalAdjusters.next(
                                DayOfWeek.MONDAY
                        )
                );
    }

    // ==================================================================

    @Test
    @DisplayName("Giờ hoạt động đọc đúng (open < close) và availability trả khung giờ trống")
    void availabilityMatchesOperatingHours() {

        LocalDate date =
                nextMonday();

        BranchOperatingHour hours = operatingHours
                .findByBranchIdAndDayOfWeek(
                        BRANCH_ID,
                        date.getDayOfWeek()
                                .getValue()
                )
                .orElseThrow();

        assertTrue(
                hours.getOpenTime()
                        .isBefore(
                                hours.getCloseTime()
                        ),
                "Giờ mở cửa phải trước giờ đóng cửa: "
                        + hours.getOpenTime()
                        + " - "
                        + hours.getCloseTime()
        );

        List<AvailabilitySlotResponse> slots =
                bookingService.availability(
                        admin,
                        facility.getId(),
                        date
                );

        assertFalse(
                slots.isEmpty(),
                "Phải có khung giờ trống ngày " + date
        );

        // Khung giờ đầu tiên = giờ mở cửa của chi nhánh
        assertEquals(
                hours.getOpenTime(),
                slots.get(0)
                        .startsAtUtc()
                        .atZone(TimeUtil.VIETNAM)
                        .toLocalTime(),
                "Khung giờ đầu phải đúng giờ mở cửa"
        );

        // Không khung giờ nào vượt giờ đóng cửa
        LocalTime lastEnd = slots.get(slots.size() - 1)
                .endsAtUtc()
                .atZone(TimeUtil.VIETNAM)
                .toLocalTime();

        assertFalse(
                lastEnd.isAfter(
                        hours.getCloseTime()
                ),
                "Khung giờ cuối vượt giờ đóng cửa: " + lastEnd
        );
    }

    @Test
    @DisplayName("Đặt lịch trong giờ hoạt động phải THÀNH CÔNG (trước đây bị chê ngoài giờ)")
    void createInsideOperatingHoursSucceeds() {

        LocalDate date =
                nextMonday();

        List<AvailabilitySlotResponse> slots =
                bookingService.availability(
                        member,
                        facility.getId(),
                        date
                );

        assertFalse(
                slots.isEmpty()
        );

        AvailabilitySlotResponse slot =
                slots.get(0);

        BookingResponse booking =
                null;

        try {

            booking = bookingService.create(
                    member,
                    new BookingCreateRequest(
                            member.getMemberId(),
                            facility.getBranchId(),
                            ServiceCode.GYM,
                            facility.getId(),
                            slot.startsAtUtc()
                    )
            );

            assertNotNull(
                    booking.id()
            );

            assertEquals(
                    BookingStatus.CONFIRMED,
                    booking.status()
            );

            assertEquals(
                    slot.startsAtUtc(),
                    booking.startsAtUtc()
            );

        } finally {

            if (booking != null) {

                bookingRepository.findById(
                                booking.id()
                        )
                        .ifPresent(
                                bookingRepository::delete
                        );
            }
        }
    }

    @Test
    @DisplayName("Đặt lịch ngoài giờ hoạt động vẫn bị từ chối sau khi đổi cách đọc giờ")
    void createOutsideOperatingHoursRejected() {

        LocalDate date =
                nextMonday();

        Instant outside = date.atTime(
                        LocalTime.of(
                                3,
                                0
                        )
                )
                .atZone(TimeUtil.VIETNAM)
                .toInstant();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> bookingService.create(
                        member,
                        new BookingCreateRequest(
                                member.getMemberId(),
                                facility.getBranchId(),
                                ServiceCode.GYM,
                                facility.getId(),
                                outside
                        )
                )
        );

        assertEquals(
                "booking_outside_operating_hours",
                exception.getCode()
        );
    }
}
