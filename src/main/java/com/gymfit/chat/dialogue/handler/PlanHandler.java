package com.gymfit.chat.dialogue.handler;

import com.gymfit.branch.ServiceCode;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.plan.PlanManagementService;
import com.gymfit.plan.PlanStatus;
import com.gymfit.plan.PlanTier;
import com.gymfit.plan.dto.PlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gói tập: danh sách, chi tiết, so sánh, gợi ý.
 * <p>Gợi ý gói là <b>rule</b> (không ML): lọc theo dịch vụ → lọc theo ngân sách →
 * sắp theo giá/ngày hoặc số dịch vụ.
 */
@Component
@RequiredArgsConstructor
public class PlanHandler
        implements IntentHandler {

    private static final int MAX_LIST =
            8;

    private final PlanManagementService planService;
    private final ResponseTemplates templates;
    private final BranchSupport branchSupport;

    @Override
    public Set<Intent> supports() {
        return Set.of(
                Intent.LIST_PLANS,
                Intent.PLAN_DETAIL,
                Intent.PLAN_COMPARE,
                Intent.PLAN_RECOMMEND
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        try {

            List<PlanResponse> plans =
                    planService.list(
                            context.principal(),
                            context.entities()
                                    .branchId(),
                            PlanStatus.ACTIVE
                    );

            if (plans == null
                    || plans.isEmpty()) {
                return IntentHandler.message(
                        context,
                        templates.get(
                                "info.no_data"
                        )
                );
            }

            return switch (context.intent()) {
                case LIST_PLANS ->
                        list(
                                context,
                                plans
                        );

                case PLAN_DETAIL ->
                        detail(
                                context,
                                plans
                        );

                case PLAN_COMPARE ->
                        compare(
                                context,
                                plans
                        );

                default ->
                        recommend(
                                context,
                                plans
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
    // LIST_PLANS
    // ------------------------------------------------------------------

    private ChatResponse list(
            HandlerContext context,
            List<PlanResponse> plans
    ) {

        List<PlanResponse> filtered =
                filter(
                        plans,
                        context
                );

        if (filtered.isEmpty()) {
            return IntentHandler.message(
                    context,
                    "Không có gói nào khớp yêu cầu của bạn."
            );
        }

        List<PlanResponse> shown =
                filtered.stream()
                        .limit(MAX_LIST)
                        .toList();

        String body =
                shown.stream()
                        .map(this::line)
                        .collect(
                                Collectors.joining("\n")
                        );

        String message =
                body;

        if (filtered.size() > MAX_LIST) {
            message = message + "\n"
                    + templates.get(
                    "plan.more",
                    Map.of(
                            "count",
                            filtered.size() - MAX_LIST
                    )
            );
        }

        return IntentHandler.message(
                context,
                message,
                List.of(
                        ChatSuggestion.of(
                                "Gói nào phù hợp với tôi"
                        ),
                        ChatSuggestion.of(
                                "So sánh các gói"
                        )
                )
        );
    }

    private String line(
            PlanResponse plan
    ) {

        return templates.get(
                "plan.item",
                Map.of(
                        "name",
                        plan.name(),
                        "tier",
                        tier(
                                plan.tier()
                        ),
                        "price",
                        Fmt.money(
                                plan.price()
                        ),
                        "days",
                        plan.durationDays(),
                        "services",
                        services(
                                plan
                        )
                )
        );
    }

    // ------------------------------------------------------------------
    // PLAN_DETAIL
    // ------------------------------------------------------------------

    private ChatResponse detail(
            HandlerContext context,
            List<PlanResponse> plans
    ) {

        List<PlanResponse> matches =
                plans.stream()
                        .filter(plan ->
                                matchesTier(
                                        plan,
                                        context
                                )
                        )
                        .filter(plan ->
                                plan.durationDays() != null
                                        && plan.durationDays()
                                        .equals(
                                                context.entities()
                                                        .durationDays()
                                        )
                        )
                        .filter(plan ->
                                matchesService(
                                        plan,
                                        context
                                )
                        )
                        .toList();

        if (matches.isEmpty()) {

            // Không có tier/duration cụ thể → coi như hỏi danh sách
            return list(
                    context,
                    plans
            );
        }

        if (matches.size() > 1) {

            String body =
                    matches.stream()
                            .limit(MAX_LIST)
                            .map(this::line)
                            .collect(
                                    Collectors.joining("\n")
                            );

            return IntentHandler.message(
                    context,
                    templates.get(
                            "plan.detail.none"
                            ) + "\n" + body,
                    matches.stream()
                            .limit(6)
                            .map(plan ->
                                    ChatSuggestion.of(
                                            plan.name()
                                    ))
                            .toList()
            );
        }

        PlanResponse plan =
                matches.get(0);

        String message =
                templates.get(
                        "plan.detail",
                        Map.of(
                                "name",
                                plan.name(),
                                "code",
                                plan.planCode(),
                                "tier",
                                tier(
                                        plan.tier()
                                ),
                                "price",
                                Fmt.money(
                                        plan.price()
                                ),
                                "per_day",
                                perDay(
                                        plan
                                ),
                                "days",
                                plan.durationDays(),
                                "services",
                                services(
                                        plan
                                ),
                                "branch",
                                plan.branchId() == null
                                        ? ""
                                        : "Chi nhánh #"
                                        + plan.branchId(),
                                "description",
                                plan.description() == null
                                        ? ""
                                        : plan.description()
                        )
                );

        return IntentHandler.message(
                context,
                message,
                List.of(
                        ChatSuggestion.of(
                                "So sánh các gói"
                        ),
                        ChatSuggestion.of(
                                "Gói nào phù hợp với tôi"
                        )
                )
        );
    }

    // ------------------------------------------------------------------
    // PLAN_COMPARE
    // ------------------------------------------------------------------

    private ChatResponse compare(
            HandlerContext context,
            List<PlanResponse> plans
    ) {

        List<PlanResponse> selected =
                plans.stream()
                        .filter(plan ->
                                matchesTier(
                                        plan,
                                        context
                                )
                        )
                        .limit(2)
                        .toList();

        if (selected.size() < 2) {

            // So sánh 2 gói rẻ nhất khác tier cùng chi nhánh
            selected =
                    plans.stream()
                            .collect(
                                    Collectors.toMap(
                                            PlanResponse::tier,
                                            plan -> plan,
                                            (a, b) -> a
                                    )
                            )
                            .values()
                            .stream()
                            .sorted(
                                    Comparator.comparing(
                                            this::perDayValue
                                    )
                            )
                            .limit(2)
                            .collect(
                                    Collectors.toList()
                            );
        }

        if (selected.size() < 2) {
            return IntentHandler.message(
                    context,
                    "Hiện chưa đủ gói để so sánh."
            );
        }

        String rows =
                "• Tên | Giá | Thời hạn | Dịch vụ | Giá/ngày\n"
                        + selected.stream()
                        .map(plan ->
                                "• " + plan.name()
                                        + " | " + Fmt.money(
                                        plan.price()
                                )
                                        + " | " + plan.durationDays()
                                        + " ngày | " + services(
                                        plan
                                )
                                        + " | " + perDay(
                                        plan))
                        .collect(
                                Collectors.joining("\n")
                        );

        String verdict =
                "Gói rẻ hơn mỗi ngày: "
                        + selected.stream()
                        .min(
                                Comparator.comparing(
                                        this::perDayValue
                                )
                        )
                        .map(
                                PlanResponse::name)
                        .orElse("");

        return IntentHandler.message(
                context,
                templates.get(
                        "plan.compare",
                        Map.of(
                                "rows",
                                rows,
                                "verdict",
                                verdict
                        )
                )
        );
    }

    // ------------------------------------------------------------------
    // PLAN_RECOMMEND (rule, không ML)
    // ------------------------------------------------------------------

    private ChatResponse recommend(
            HandlerContext context,
            List<PlanResponse> plans
    ) {

        Set<ServiceCode> wanted =
                context.entities()
                        .services();

        BigDecimal budget =
                context.entities()
                        .money();

        if (wanted.isEmpty()
                && budget == null) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "plan.recommend.need_service"
                    ),
                    List.of(
                            ChatSuggestion.of("Gym"),
                            ChatSuggestion.of("Boxing"),
                            ChatSuggestion.of("Pickleball"),
                            ChatSuggestion.of("Cả 3")
                    )
            );
        }

        List<PlanResponse> candidates =
                plans.stream()
                        .filter(plan ->
                                wanted.isEmpty()
                                        || plan.services()
                                        .containsAll(
                                                wanted
                                        )
                        )
                        .filter(plan ->
                                budget == null
                                        || plan.price()
                                        .compareTo(
                                                budget
                                        ) <= 0
                        )
                        .toList();

        if (candidates.isEmpty()) {

            candidates =
                    plans.stream()
                            .filter(plan ->
                                    wanted.isEmpty()
                                            || plan.services()
                                            .containsAll(
                                                    wanted
                                            )
                            )
                            .toList();
        }

        if (candidates.isEmpty()) {
            return IntentHandler.message(
                    context,
                    "Không có gói nào phù hợp với yêu cầu của bạn."
            );
        }

        boolean cheapest =
                context.text()
                        .plain()
                        .contains("re")
                        && context.text()
                                .plain()
                                .contains("nhat");

        boolean full =
                context.text()
                        .plain()
                        .contains("day du")
                        || context.text()
                                .plain()
                                .contains("premium");

        @SuppressWarnings({"unchecked", "rawtypes"})
    Comparator<PlanResponse> order =
                full
                        ? Comparator.comparingInt(
                        (PlanResponse plan) ->
                                plan.services()
                                        .size())
                        .thenComparing(
                                this::perDayValue)
                        : Comparator.comparing(
                                this::perDayValue);

        List<PlanResponse> top =
                candidates.stream()
                        .sorted(order)
                        .limit(3)
                        .toList();

        String body =
                top.stream()
                        .map(this::line)
                        .collect(
                                Collectors.joining("\n")
                        );

        String reason =
                wanted.isEmpty()
                        ? "lọc theo ngân sách "
                        + Fmt.money(
                        budget)
                        + " và sắp theo giá/ngày"
                        : "gói có đủ dịch vụ "
                        + wanted.stream()
                        .map(
                                Fmt::service)
                        .collect(
                                Collectors.joining(", ")
                        )
                        + (budget == null
                        ? ""
                        : ", giá ≤ " + Fmt.money(
                        budget))
                        + (cheapest ? ", ưu tiên rẻ nhất" : "");

        return IntentHandler.message(
                context,
                templates.get(
                        "plan.recommend",
                        Map.of(
                                "plans",
                                body,
                                "reason",
                                reason
                        )
                ),
                List.of(
                        ChatSuggestion.of(
                                "So sánh các gói"
                        ),
                        ChatSuggestion.of(
                                "Gói nào rẻ nhất"
                        )
                )
        );
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private List<PlanResponse> filter(
            List<PlanResponse> plans,
            HandlerContext context
    ) {

        return plans.stream()
                .filter(plan ->
                        matchesService(
                                plan,
                                context
                        )
                )
                .sorted(
                        Comparator.comparing(
                                this::perDayValue
                        )
                )
                .toList();
    }

    private boolean matchesService(
            PlanResponse plan,
            HandlerContext context
    ) {

        Set<ServiceCode> wanted =
                context.entities()
                        .services();

        return wanted.isEmpty()
                || plan.services()
                .containsAll(
                        wanted
                );
    }

    private boolean matchesTier(
            PlanResponse plan,
            HandlerContext context
    ) {

        PlanTier wanted =
                context.entities()
                        .tier();

        return wanted == null
                || wanted == plan.tier();
    }

    /**
     * Danh sách dịch vụ, sắp theo tên enum để câu trả lời ổn định
     * ({@code Set} không có thứ tự → output có thể đổi giữa các lần chạy).
     */
    private String services(
            PlanResponse plan
    ) {
        return plan.services()
                .stream()
                .sorted(
                        Comparator.comparing(
                                ServiceCode::name
                        )
                )
                .map(
                        Fmt::service)
                .collect(
                        Collectors.joining(", ")
                );
    }

    private String tier(
            PlanTier tier
    ) {
        return tier == null
                ? ""
                : tier.name();
    }

    private BigDecimal perDayValue(
            PlanResponse plan
    ) {

        if (plan.price() == null
                || plan.durationDays() == null
                || plan.durationDays() <= 0) {
            return BigDecimal.valueOf(
                    Long.MAX_VALUE
            );
        }

        return plan.price()
                .divide(
                        BigDecimal.valueOf(
                                plan.durationDays()
                        ),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private String perDay(
            PlanResponse plan
    ) {
        return Fmt.money(
                perDayValue(
                        plan
                )
        );
    }
}