package com.gymfit.chat.dialogue.handler;

import com.gymfit.branch.BranchService;
import com.gymfit.branch.BranchStatus;
import com.gymfit.branch.FacilityService;
import com.gymfit.branch.FacilityStatus;
import com.gymfit.branch.ServiceCode;
import com.gymfit.branch.dto.BranchResponse;
import com.gymfit.branch.dto.BranchServiceResponse;
import com.gymfit.branch.dto.FacilityResponse;
import com.gymfit.branch.dto.OperatingHourResponse;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thông tin chi nhánh: địa chỉ, giờ mở cửa, dịch vụ, cơ sở vật chất.
 * <p>Chi nhánh lấy từ entity; không có thì dùng chi nhánh của membership
 * (member) hoặc liệt kê tất cả chi nhánh ACTIVE.
 */
@Component
@RequiredArgsConstructor
public class BranchInfoHandler
        implements IntentHandler {

    private final BranchService branchService;
    private final FacilityService facilityService;
    private final ResponseTemplates templates;

    private final BranchSupport branchSupport;

    @Override
    public Set<Intent> supports() {
        return Set.of(
                Intent.BRANCH_INFO,
                Intent.OPERATING_HOURS,
                Intent.LIST_SERVICES,
                Intent.LIST_FACILITIES
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        try {

            List<BranchResponse> branches =
                    branchService.list(
                            BranchStatus.ACTIVE
                    );

            if (branches.isEmpty()) {
                return IntentHandler.message(
                        context,
                        templates.get(
                                "info.no_data"
                        )
                );
            }

            Optional<BranchResponse> target =
                    resolve(
                            context,
                            branches
                    );

            return switch (context.intent()) {
                case BRANCH_INFO ->
                        branchInfo(
                                context,
                                target,
                                branches
                        );

                case OPERATING_HOURS ->
                        operatingHours(
                                context,
                                target
                        );

                case LIST_SERVICES ->
                        services(
                                context,
                                target
                        );

                default ->
                        facilities(
                                context,
                                target
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
    // Chọn chi nhánh
    // ------------------------------------------------------------------

    private Optional<BranchResponse> resolve(
            HandlerContext context,
            List<BranchResponse> branches
    ) {

        Long requested =
                context.entities()
                        .branchId();

        if (requested != null) {

            Optional<BranchResponse> found =
                    branches.stream()
                            .filter(branch ->
                                    branch.id()
                                            .equals(requested)
                            )
                            .findFirst();

            if (found.isPresent()) {
                return found;
            }

            // Chi nhánh nói ra không tồn tại → không bịa.
            return Optional.empty();
        }

        return Optional.of(
                branchSupport.defaultBranch(
                        context,
                        branches
                )
        );
    }

    // ------------------------------------------------------------------
    // Từng intent
    // ------------------------------------------------------------------

    private ChatResponse branchInfo(
            HandlerContext context,
            Optional<BranchResponse> target,
            List<BranchResponse> branches
    ) {

        if (target.isEmpty()) {
            return IntentHandler.message(
                    context,
                    branchList(
                            branches
                    )
            );
        }

        BranchResponse branch =
                target.get();

        String message =
                templates.get(
                        "branch.info",
                        Map.of(
                                "code",
                                branch.code(),
                                "name",
                                branch.name(),
                                "address",
                                branch.address(),
                                "phone",
                                branch.phone() == null
                                        ? "không cập nhật"
                                        : branch.phone(),
                                "status",
                                branch.status()
                                        .name()
                        )
                );

        return IntentHandler.message(
                context,
                message,
                List.of(
                        ChatSuggestion.of(
                                "Giờ mở cửa "
                                        + branch.code()
                        ),
                        ChatSuggestion.of(
                                "Dịch vụ của "
                                        + branch.code()
                        ),
                        ChatSuggestion.of(
                                "Các chi nhánh"
                        )
                )
        );
    }

    private String branchList(
            List<BranchResponse> branches
    ) {

        String body =
                branches.stream()
                        .map(branch ->
                                "• " + branch.code()
                                        + " — " + branch.name()
                                        + " (" + branch.address() + ")")
                        .reduce(
                                (a, b) -> a + "\n" + b
                        )
                        .orElse("");

        return templates.get(
                "branch.list",
                Map.of(
                        "branches",
                        body
                )
        );
    }

    private ChatResponse operatingHours(
            HandlerContext context,
            Optional<BranchResponse> target
    ) {

        if (target.isEmpty()) {
            return IntentHandler.message(
                    context,
                    "Mình không tìm thấy chi nhánh bạn hỏi."
            );
        }

        BranchResponse branch =
                target.get();

        List<OperatingHourResponse> hours =
                branchService.getOperatingHours(
                        branch.id()
                );

        if (hours == null
                || hours.isEmpty()) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "hours.empty",
                            Map.of(
                                    "branch",
                                    branch.code()
                            )
                    )
            );
        }

        String body =
                hours.stream()
                        .map(hour ->
                                "• " + Fmt.dayName(
                                        hour.dayOfWeek()
                                ) + ": "
                                        + hour.openTime()
                                        + " - " + hour.closeTime())
                        .reduce(
                                (a, b) -> a + "\n" + b
                        )
                        .orElse("");

        return IntentHandler.message(
                context,
                templates.get(
                        "hours",
                        Map.of(
                                "branch",
                                branch.code(),
                                "hours",
                                body
                        )
                ),
                List.of(
                        ChatSuggestion.of(
                                "Xem slot trống "
                                        + branch.code()
                        )
                )
        );
    }

    private ChatResponse services(
            HandlerContext context,
            Optional<BranchResponse> target
    ) {

        if (target.isEmpty()) {
            return IntentHandler.message(
                    context,
                    "Mình không tìm thấy chi nhánh bạn hỏi."
            );
        }

        BranchResponse branch =
                target.get();

        List<BranchServiceResponse> services =
                branchService.getServices(
                        branch.id()
                );

        if (services == null
                || services.isEmpty()) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "services",
                            Map.of(
                                    "branch",
                                    branch.code(),
                                    "services",
                                    "chưa có dịch vụ nào"
                            )
                    )
            );
        }

        String body =
                services.stream()
                        .map(service ->
                                "• " + Fmt.service(
                                        service.serviceCode()
                                ) + " — "
                                        + service.bookingDurationMinutes()
                                        + " phút/lượt, "
                                        + (Boolean.TRUE.equals(
                                        service.bookingEnabled()
                                ) ? "cho đặt lịch" : "tạm không cho đặt")
                                        + " (sức chứa " + service.capacity() + ")")
                        .reduce(
                                (a, b) -> a + "\n" + b
                        )
                        .orElse("");

        return IntentHandler.message(
                context,
                templates.get(
                        "services",
                        Map.of(
                                "branch",
                                branch.code(),
                                "services",
                                body
                        )
                )
        );
    }

    private ChatResponse facilities(
            HandlerContext context,
            Optional<BranchResponse> target
    ) {

        if (target.isEmpty()) {
            return IntentHandler.message(
                    context,
                    "Mình không tìm thấy chi nhánh bạn hỏi."
            );
        }

        BranchResponse branch =
                target.get();

        ServiceCode service =
                context.entities()
                        .services()
                        .stream()
                        .findFirst()
                        .orElse(null);

        List<FacilityResponse> facilities =
                facilityService.list(
                        context.principal(),
                        branch.id(),
                        service,
                        FacilityStatus.ACTIVE
                );

        if (facilities == null
                || facilities.isEmpty()) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "info.no_data"
                    )
            );
        }

        String body =
                facilities.stream()
                        .map(facility ->
                                "• " + facility.name()
                                        + " (" + Fmt.service(
                                        facility.serviceCode()
                                ) + ") — sức chứa "
                                        + facility.capacity())
                        .reduce(
                                (a, b) -> a + "\n" + b
                        )
                        .orElse("");

        return IntentHandler.message(
                context,
                templates.get(
                        "facilities",
                        Map.of(
                                "branch",
                                branch.code(),
                                "facilities",
                                body
                        )
                )
        );
    }

}