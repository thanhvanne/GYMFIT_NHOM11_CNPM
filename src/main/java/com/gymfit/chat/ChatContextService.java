package com.gymfit.chat;

import com.gymfit.branch.Branch;
import com.gymfit.branch.BranchRepository;
import com.gymfit.branch.ServiceCode;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.member.Member;
import com.gymfit.member.MemberRepository;
import com.gymfit.membership.Membership;
import com.gymfit.membership.MembershipRepository;
import com.gymfit.membership.MembershipServiceAccessRepository;
import com.gymfit.membership.MembershipStatus;
import com.gymfit.plan.MembershipPlan;
import com.gymfit.plan.MembershipPlanRepository;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.gymfit.booking.Booking;
import com.gymfit.booking.BookingRepository;
import com.gymfit.booking.BookingStatus;
import com.gymfit.branch.BranchOperatingHour;
import com.gymfit.branch.BranchOperatingHourRepository;
import com.gymfit.common.util.TimeUtil;

import java.time.LocalDate;
import java.util.List;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatContextService {

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipServiceAccessRepository accessRepository;
    private final MembershipPlanRepository planRepository;
    private final BranchRepository branchRepository;
    private final BookingRepository bookingRepository;
    private final BranchOperatingHourRepository operatingHourRepository;

    public String build(
            AppPrincipal principal
    ) {
        return switch (principal.getRole()) {
            case MEMBER ->
                    memberContext(principal);

            case BRANCH_MANAGER ->
                    managerContext(principal);

            case ADMIN ->
                    adminContext();
        };
    }

    private String memberContext(
            AppPrincipal principal
    ) {
        if (principal.getMemberId() == null) {
            return """
                    Role: MEMBER
                    Hồ sơ hội viên chưa được liên kết.
                    """;
        }

        Member member =
                memberRepository
                        .findById(
                                principal.getMemberId()
                        )
                        .orElse(null);

        if (member == null) {
            return """
                    Role: MEMBER
                    Không tìm thấy hồ sơ hội viên.
                    """;
        }

        StringBuilder context =
                new StringBuilder();

        context.append(
                "Role: MEMBER\n"
        );

        context.append(
                "Tên hội viên: "
        ).append(
                member.getFullName()
        ).append("\n");

        context.append(
                "Mã hội viên: "
        ).append(
                member.getMemberCode()
        ).append("\n");

        context.append(
                "Trạng thái hội viên: "
        ).append(
                member.getStatus()
        ).append("\n");

        Branch homeBranch =
                branchRepository
                        .findById(
                                member.getHomeBranchId()
                        )
                        .orElse(null);

        if (homeBranch != null) {
            context.append(
                    "Chi nhánh chính: "
            ).append(
                    homeBranch.getName()
            ).append("\n");
        }

        Membership membership =
                membershipRepository
                        .findByMemberIdAndStatus(
                                member.getId(),
                                MembershipStatus.ACTIVE
                        )
                        .orElse(null);

        if (membership == null) {
            context.append(
                    "Membership hiện tại: Không có\n"
            );

            return context.toString();
        }

        MembershipPlan plan =
                planRepository
                        .findById(
                                membership.getPlanId()
                        )
                        .orElse(null);

        context.append(
                "Membership hiện tại: ACTIVE\n"
        );

        if (plan != null) {
            context.append(
                    "Tên gói: "
            ).append(
                    plan.getName()
            ).append("\n");

            context.append(
                    "Thời hạn gói: "
            ).append(
                    plan.getDurationDays()
            ).append(
                    " ngày\n"
            );
        }

        context.append(
                "Ngày bắt đầu: "
        ).append(
                membership.getStartDate()
        ).append("\n");

        context.append(
                "Ngày kết thúc: "
        ).append(
                membership.getEndDate()
        ).append("\n");

        Branch membershipBranch =
                branchRepository
                        .findById(
                                membership.getBranchId()
                        )
                        .orElse(null);

        if (membershipBranch != null) {
            context.append(
                    "Chi nhánh membership: "
            ).append(
                    membershipBranch.getName()
            ).append("\n");
        }

        String services =
                accessRepository
                        .findAllByIdMembershipId(
                                membership.getId()
                        )
                        .stream()
                        .map(access ->
                                access.getId()
                                        .getServiceCode()
                        )
                        .map(ServiceCode::name)
                        .collect(
                                Collectors.joining(", ")
                        );

        context.append(
                "Dịch vụ được sử dụng: "
        ).append(
                services.isBlank()
                        ? "Không có"
                        : services
        ).append("\n");

        context.append("\nBOOKING CỦA HỘI VIÊN\n");

        List<Booking> bookings =
                bookingRepository
                        .findAllByMemberIdOrderByStartsAtUtcDesc(
                                member.getId()
                        );

        List<Booking> upcoming =
                bookings.stream()
                        .filter(booking ->
                                booking.getStatus()
                                        == BookingStatus.CONFIRMED
                        )
                        .filter(booking ->
                                booking.getStartsAtUtc()
                                        .isAfter(TimeUtil.now())
                        )
                        .limit(10)
                        .toList();

        if (upcoming.isEmpty()) {
            context.append(
                    "Không có booking sắp tới.\n"
            );
        } else {
            for (Booking booking : upcoming) {
                context.append("- ")
                        .append(booking.getServiceCode())
                        .append(" | ")
                        .append(
                                booking.getStartsAtUtc()
                                        .atZone(TimeUtil.VIETNAM)
                                        .toLocalDateTime()
                        )
                        .append(" | branchId=")
                        .append(booking.getBranchId())
                        .append("\n");
            }
        }

        if (membershipBranch != null) {
            context.append(
                    "\nGIỜ HOẠT ĐỘNG CHI NHÁNH MEMBERSHIP\n"
            );

            List<BranchOperatingHour> hours =
                    operatingHourRepository
                            .findAllByBranchIdOrderByDayOfWeekAsc(
                                    membershipBranch.getId()
                            );

            for (BranchOperatingHour hour : hours) {
                context.append("- ")
                        .append(dayName(
                                hour.getDayOfWeek()
                        ))
                        .append(": ")
                        .append(hour.getOpenTime())
                        .append(" - ")
                        .append(hour.getCloseTime())
                        .append("\n");
            }
        }

        return context.toString();
    }

    private String managerContext(
            AppPrincipal principal
    ) {
        Branch branch =
                principal.getBranchId() == null
                        ? null
                        : branchRepository
                        .findById(
                                principal.getBranchId()
                        )
                        .orElse(null);

        return """
                Role: BRANCH_MANAGER
                Branch ID: %s
                Chi nhánh quản lý: %s

                Người dùng chỉ được phép xem dữ liệu thuộc chi nhánh này.
                """.formatted(
                principal.getBranchId(),
                branch == null
                        ? "Không xác định"
                        : branch.getName()
        );
    }

    private String adminContext() {
        return """
                Role: ADMIN
                Phạm vi truy cập: Toàn bộ hệ thống GYMFIT.
                """;
    }

    private String dayName(Integer day) {
        return switch (day) {
            case 1 -> "Thứ 2";
            case 2 -> "Thứ 3";
            case 3 -> "Thứ 4";
            case 4 -> "Thứ 5";
            case 5 -> "Thứ 6";
            case 6 -> "Thứ 7";
            case 7 -> "Chủ nhật";
            default -> "Không xác định";
        };
    }
}