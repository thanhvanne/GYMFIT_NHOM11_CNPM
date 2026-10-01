package com.gymfit.booking;
import com.gymfit.audit.AuditService;

import java.util.Map;

import com.gymfit.booking.dto.*;
import com.gymfit.branch.*;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.error.NotFoundException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.BranchScopeGuard;
import com.gymfit.common.util.CodeGenerator;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.member.MemberStatus;
import com.gymfit.membership.Membership;
import com.gymfit.membership.MembershipService;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymfit.membership.MembershipRepository;
import com.gymfit.membership.MembershipStatus;

import java.time.*;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final MemberRepository memberRepository;
    private final BranchRepository branchRepository;
    private final BranchServiceConfigRepository serviceConfigRepository;
    private final BranchOperatingHourRepository operatingHourRepository;
    private final FacilityRepository facilityRepository;
    private final MembershipService membershipService;
    private final BranchScopeGuard branchScopeGuard;
    private final AuditService auditService;
    private final MembershipRepository membershipRepository;

    @Transactional
    public BookingResponse create(
            AppPrincipal principal,
            BookingCreateRequest request
    ) {
        Long memberId = resolveMemberId(
                principal,
                request.memberId()
        );

        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new NotFoundException(
                        "member_not_found",
                        "Không tìm thấy hội viên"
                ));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ConflictException(
                    "member_inactive",
                    "Hội viên không hoạt động"
            );
        }

        requireCreateScope(
                principal,
                member,
                request.branchId()
        );

        Branch branch = branchRepository
                .findById(request.branchId())
                .orElseThrow(() -> new NotFoundException(
                        "branch_not_found",
                        "Không tìm thấy chi nhánh"
                ));

        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new ConflictException(
                    "branch_inactive",
                    "Chi nhánh không hoạt động"
            );
        }

        BranchServiceConfig config = serviceConfigRepository
                .findByIdBranchIdAndIdServiceCode(
                        branch.getId(),
                        request.serviceCode()
                )
                .orElseThrow(() -> new ConflictException(
                        "service_not_supported",
                        "Chi nhánh không hỗ trợ dịch vụ này"
                ));

        if (!Boolean.TRUE.equals(config.getBookingEnabled())) {
            throw new ConflictException(
                    "booking_disabled",
                    "Dịch vụ hiện không cho phép đặt lịch"
            );
        }

        Facility facility = facilityRepository
                .findByIdForUpdate(request.facilityId())
                .orElseThrow(() -> new NotFoundException(
                        "facility_not_found",
                        "Không tìm thấy cơ sở vật chất"
                ));

        if (!facility.getBranchId().equals(branch.getId())) {
            throw new ConflictException(
                    "facility_branch_mismatch",
                    "Cơ sở vật chất không thuộc chi nhánh này"
            );
        }

        if (facility.getServiceCode() != request.serviceCode()) {
            throw new ConflictException(
                    "facility_service_mismatch",
                    "Cơ sở vật chất không thuộc dịch vụ đã chọn"
            );
        }

        if (facility.getStatus() != FacilityStatus.ACTIVE) {
            throw new ConflictException(
                    "facility_unavailable",
                    "Cơ sở vật chất hiện không hoạt động"
            );
        }

        Membership membership = membershipService.requireEligible(
                memberId,
                branch.getId(),
                request.serviceCode()
        );

        Instant now = TimeUtil.now();

        if (!request.startsAt().isAfter(now)) {
            throw new ConflictException(
                    "booking_not_future",
                    "Thời gian đặt lịch phải ở tương lai"
            );
        }

        Instant endsAt = request.startsAt()
                .plusSeconds(
                        config.getBookingDurationMinutes() * 60L
                );

        validateOperatingHours(
                branch,
                request.startsAt(),
                endsAt
        );

        if (bookingRepository.countMemberOverlap(
                memberId,
                request.startsAt(),
                endsAt
        ) > 0) {
            throw new ConflictException(
                    "booking_overlap",
                    "Bạn đã có lịch tập trùng thời gian"
            );
        }

        long occupied = bookingRepository.countFacilityOverlap(
                facility.getId(),
                request.startsAt(),
                endsAt
        );

        int effectiveCapacity = Math.min(
                facility.getCapacity(),
                config.getCapacity()
        );

        if (occupied >= effectiveCapacity) {
            throw new ConflictException(
                    "facility_full",
                    "Khung giờ đã đủ số lượng người"
            );
        }

        Booking booking = Booking.builder()
                .bookingCode(CodeGenerator.generate("BOOK"))
                .memberId(memberId)
                .membershipId(membership.getId())
                .branchId(branch.getId())
                .serviceCode(request.serviceCode())
                .facilityId(facility.getId())
                .status(BookingStatus.CONFIRMED)
                .startsAtUtc(request.startsAt())
                .endsAtUtc(endsAt)
                .cancelledAtUtc(null)
                .cancellationReason(null)
                .createdByUserId(principal.getUserId())
                .createdAtUtc(TimeUtil.now())
                .build();

        Booking saved = bookingRepository.save(booking);

        auditService.record(
                principal.getUserId(),
                "BOOKING_CREATED",
                "BOOKING",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "memberId", saved.getMemberId(),
                        "serviceCode", saved.getServiceCode().name(),
                        "facilityId", saved.getFacilityId()
                )
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(
            AppPrincipal principal
    ) {
        List<Booking> bookings;

        if (principal.getRole() == RoleCode.ADMIN) {
            bookings = bookingRepository
                    .findAllByOrderByStartsAtUtcDesc();
        } else if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            bookings = bookingRepository
                    .findAllByBranchIdOrderByStartsAtUtcDesc(
                            principal.getBranchId()
                    );
        } else {
            if (principal.getMemberId() == null) {
                throw new ForbiddenException(
                        "member_scope_missing",
                        "Tài khoản chưa liên kết hội viên"
                );
            }

            bookings = bookingRepository
                    .findAllByMemberIdOrderByStartsAtUtcDesc(
                            principal.getMemberId()
                    );
        }

        return bookings.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(
            AppPrincipal principal,
            Long bookingId
    ) {
        Booking booking = requireBooking(bookingId);

        requireReadScope(principal, booking);

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancel(
            AppPrincipal principal,
            Long bookingId,
            BookingCancelRequest request
    ) {
        Booking booking = requireBooking(bookingId);

        requireReadScope(principal, booking);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException(
                    "booking_already_cancelled",
                    "Lịch đặt đã được hủy"
            );
        }

        if (booking.getStatus() == BookingStatus.COMPLETED
                || booking.getEndsAtUtc().isBefore(TimeUtil.now())) {
            throw new ConflictException(
                    "booking_completed",
                    "Lịch đặt đã hoàn thành"
            );
        }

        Instant now = TimeUtil.now();

        if (!booking.getStartsAtUtc().isAfter(now)) {
            throw new ConflictException(
                    "booking_already_started",
                    "Lịch đặt đã bắt đầu"
            );
        }

        if (principal.getRole() == RoleCode.MEMBER) {
            Instant deadline = booking.getStartsAtUtc()
                    .minus(Duration.ofHours(2));

            if (now.isAfter(deadline)) {
                throw new ConflictException(
                        "booking_cancellation_too_late",
                        "Chỉ được hủy lịch trước ít nhất 2 giờ"
                );
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAtUtc(now);
        booking.setCancellationReason(
                request.reason().trim()
        );

        Booking saved = bookingRepository.save(booking);

        auditService.record(
                principal.getUserId(),
                "BOOKING_CANCELLED",
                "BOOKING",
                saved.getId(),
                saved.getBranchId(),
                Map.of(
                        "memberId", saved.getMemberId(),
                        "reason", saved.getCancellationReason()
                )
        );

        return toResponse(saved);
    }

    public Booking requireBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "booking_not_found",
                        "Không tìm thấy lịch đặt"
                ));
    }

    private Long resolveMemberId(
            AppPrincipal principal,
            Long requestedMemberId
    ) {
        if (principal.getRole() == RoleCode.MEMBER) {
            if (principal.getMemberId() == null) {
                throw new ForbiddenException(
                        "member_scope_missing",
                        "Tài khoản chưa liên kết hội viên"
                );
            }

            return principal.getMemberId();
        }

        if (requestedMemberId == null) {
            throw new ConflictException(
                    "member_required",
                    "Vui lòng chọn hội viên"
            );
        }

        return requestedMemberId;
    }

    private void requireCreateScope(
            AppPrincipal principal,
            Member member,
            Long branchId
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    branchId
            );

            if (principal.getBranchId()
                    .equals(member.getHomeBranchId())) {
                return;
            }

            boolean activeMembershipAtBranch =
                    membershipRepository
                            .findByMemberIdAndStatus(
                                    member.getId(),
                                    MembershipStatus.ACTIVE
                            )
                            .map(membership ->
                                    membership
                                            .getBranchId()
                                            .equals(
                                                    principal.getBranchId()
                                            )
                            )
                            .orElse(false);

            if (!activeMembershipAtBranch) {
                throw new ForbiddenException(
                        "member_out_of_branch_scope",
                        "Hội viên nằm ngoài phạm vi chi nhánh"
                );
            }

            return;
        }

        branchScopeGuard.requireMemberSelf(
                principal,
                member.getId()
        );
    }

    private void requireReadScope(
            AppPrincipal principal,
            Booking booking
    ) {
        if (principal.getRole() == RoleCode.ADMIN) {
            return;
        }

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    booking.getBranchId()
            );
            return;
        }

        branchScopeGuard.requireMemberSelf(
                principal,
                booking.getMemberId()
        );
    }

    private void validateOperatingHours(
            Branch branch,
            Instant startsAt,
            Instant endsAt
    ) {
        ZoneId zone;

        try {
            zone = ZoneId.of(branch.getTimezone());
        } catch (Exception exception) {
            throw new ConflictException(
                    "invalid_branch_timezone",
                    "Múi giờ chi nhánh không hợp lệ"
            );
        }

        ZonedDateTime localStart = startsAt.atZone(zone);
        ZonedDateTime localEnd = endsAt.atZone(zone);

        if (!localStart.toLocalDate()
                .equals(localEnd.toLocalDate())) {
            throw new ConflictException(
                    "booking_outside_operating_hours",
                    "Lịch đặt nằm ngoài giờ hoạt động"
            );
        }

        int dayOfWeek = localStart
                .getDayOfWeek()
                .getValue();

        BranchOperatingHour hours =
                operatingHourRepository
                        .findByBranchIdAndDayOfWeek(
                                branch.getId(),
                                dayOfWeek
                        )
                        .orElseThrow(() -> new ConflictException(
                                "branch_closed",
                                "Chi nhánh không hoạt động vào ngày này"
                        ));

        LocalTime startTime = localStart.toLocalTime();
        LocalTime endTime = localEnd.toLocalTime();

        if (startTime.isBefore(hours.getOpenTime())
                || endTime.isAfter(hours.getCloseTime())) {
            throw new ConflictException(
                    "booking_outside_operating_hours",
                    "Lịch đặt nằm ngoài giờ hoạt động"
            );
        }
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getMemberId(),
                booking.getMembershipId(),
                booking.getBranchId(),
                booking.getServiceCode(),
                booking.getFacilityId(),
                effectiveStatus(booking),
                booking.getStartsAtUtc(),
                booking.getEndsAtUtc(),
                booking.getCancelledAtUtc(),
                booking.getCancellationReason(),
                booking.getCreatedByUserId(),
                booking.getCreatedAtUtc()
        );
    }

    private BookingStatus effectiveStatus(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return booking.getStatus();
        }

        if (booking.getEndsAtUtc().isBefore(TimeUtil.now())) {
            return BookingStatus.COMPLETED;
        }

        return BookingStatus.CONFIRMED;
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> availability(
            AppPrincipal principal,
            Long facilityId,
            LocalDate date
    ) {
        Facility facility = facilityRepository
                .findById(facilityId)
                .orElseThrow(() -> new NotFoundException(
                        "facility_not_found",
                        "Không tìm thấy cơ sở vật chất"
                ));

        if (principal.getRole() == RoleCode.BRANCH_MANAGER) {
            branchScopeGuard.requireBranch(
                    principal,
                    facility.getBranchId()
            );
        }

        if (facility.getStatus() != FacilityStatus.ACTIVE) {
            return List.of();
        }

        Branch branch = branchRepository
                .findById(facility.getBranchId())
                .orElseThrow(() -> new NotFoundException(
                        "branch_not_found",
                        "Không tìm thấy chi nhánh"
                ));

        if (branch.getStatus() != BranchStatus.ACTIVE) {
            return List.of();
        }

        BranchServiceConfig config = serviceConfigRepository
                .findByIdBranchIdAndIdServiceCode(
                        facility.getBranchId(),
                        facility.getServiceCode()
                )
                .orElseThrow(() -> new ConflictException(
                        "service_not_supported",
                        "Chi nhánh không hỗ trợ dịch vụ này"
                ));

        if (!Boolean.TRUE.equals(config.getBookingEnabled())) {
            return List.of();
        }

        if (principal.getRole() == RoleCode.MEMBER) {
            if (principal.getMemberId() == null) {
                throw new ForbiddenException(
                        "member_scope_missing",
                        "Tài khoản chưa liên kết hội viên"
                );
            }

            membershipService.requireEligible(
                    principal.getMemberId(),
                    facility.getBranchId(),
                    facility.getServiceCode()
            );
        }

        int day = date.getDayOfWeek().getValue();

        BranchOperatingHour hours =
                operatingHourRepository
                        .findByBranchIdAndDayOfWeek(
                                branch.getId(),
                                day
                        )
                        .orElse(null);

        if (hours == null) {
            return List.of();
        }

        ZoneId zone = ZoneId.of(branch.getTimezone());
        int duration = config.getBookingDurationMinutes();

        LocalDateTime cursor = LocalDateTime.of(
                date,
                hours.getOpenTime()
        );

        LocalDateTime closing = LocalDateTime.of(
                date,
                hours.getCloseTime()
        );

        List<AvailabilitySlotResponse> result =
                new java.util.ArrayList<>();

        while (!cursor.plusMinutes(duration).isAfter(closing)) {
            LocalDateTime localEnd =
                    cursor.plusMinutes(duration);

            Instant startInstant = cursor
                    .atZone(zone)
                    .toInstant();

            Instant endInstant = localEnd
                    .atZone(zone)
                    .toInstant();

            if (startInstant.isAfter(TimeUtil.now())) {
                long occupied =
                        bookingRepository.countFacilityOverlap(
                                facility.getId(),
                                startInstant,
                                endInstant
                        );

                int effectiveCapacity = Math.min(
                        facility.getCapacity(),
                        config.getCapacity()
                );

                int remaining = Math.max(
                        0,
                        effectiveCapacity - (int) occupied
                );

                result.add(
                        new AvailabilitySlotResponse(
                                startInstant,
                                endInstant,
                                remaining
                        )
                );
            }

            cursor = cursor.plusMinutes(duration);
        }

        return result;
    }
}