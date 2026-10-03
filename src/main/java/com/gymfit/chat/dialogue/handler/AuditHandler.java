package com.gymfit.chat.dialogue.handler;

import com.gymfit.audit.AuditService;
import com.gymfit.audit.dto.AuditEventResponse;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.Fmt;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.common.error.ApiException;
import com.gymfit.common.util.TimeUtil;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nhật ký hệ thống — chỉ ADMIN.
 * <p>{@code AuditService.list} <b>không</b> kiểm tra role, nên handler tự chặn
 * và không gọi service khi role không hợp lệ.
 */
@Component
@RequiredArgsConstructor
public class AuditHandler
        implements IntentHandler {

    private static final int LIMIT =
            10;

    private final AuditService auditService;
    private final ResponseTemplates templates;

    @Override
    public Set<Intent> supports() {
        return Set.of(
                Intent.AUDIT_RECENT
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        // Chặn TRƯỚC khi gọi service — AuditService không kiểm tra role
        if (context.principal()
                .getRole() != RoleCode.ADMIN) {

            return IntentHandler.message(
                    context,
                    templates.get(
                            "denied.role",
                            Map.of(
                                    "allowed",
                                    "Tổng quan hệ thống, doanh thu, tồn kho"
                            )
                    )
            );
        }

        try {

            List<AuditEventResponse> events =
                    auditService.list(
                            context.entities()
                                    .branchId()
                    );

            if (events == null
                    || events.isEmpty()) {
                return IntentHandler.message(
                        context,
                        templates.get(
                                "audit.none"
                        )
                );
            }

            String items =
                    events.stream()
                            .limit(LIMIT)
                            .map(event ->
                                    templates.get(
                                            "audit.item",
                                            Map.of(
                                                    "created",
                                                    Fmt.dateTime(
                                                            event.createdAtUtc(),
                                                            TimeUtil.VIETNAM
                                                    ),
                                                    "action",
                                                    event.action(),
                                                    "entity_type",
                                                    event.entityType(),
                                                    "entity_id",
                                                    event.entityId() == null
                                                            ? "-"
                                                            : event.entityId(),
                                                    "branch_label",
                                                    event.branchId() == null
                                                            ? ""
                                                            : " — chi nhánh #"
                                                            + event.branchId()
                                            )
                                    ))
                            .collect(
                                    Collectors.joining("\n")
                            );

            return IntentHandler.message(
                    context,
                    templates.get(
                            "audit.recent",
                            Map.of(
                                    "items",
                                    items
                            )
                    )
            );

        } catch (ApiException exception) {

            return fail(
                    context,
                    exception
            );
        }
    }
}