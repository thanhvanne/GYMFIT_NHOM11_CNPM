package com.gymfit.chat.dialogue.handler;

import com.gymfit.chat.dialogue.IntentPolicy;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.user.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chào / cảm ơn / tạm biệt / hỏi trợ lý / ngoài phạm vi.
 */
@Component
@RequiredArgsConstructor
public class SmallTalkHandler
        implements IntentHandler {

    private final ResponseTemplates templates;
    private final IntentPolicy policy;

    @Override
    public Set<Intent> supports() {
        return EnumSet.of(
                Intent.GREETING,
                Intent.THANKS,
                Intent.GOODBYE,
                Intent.HELP,
                Intent.OUT_OF_SCOPE
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {

        RoleCode role =
                context.principal()
                        .getRole();

        return switch (context.intent()) {
            case GREETING ->
                    IntentHandler.message(
                            context,
                            templates.first(
                                    role == RoleCode.MEMBER
                                            ? "greeting.member"
                                            : "greeting.staff",
                                    Map.of()
                            ),
                            suggestions(
                                    role
                            )
                    );

            case THANKS ->
                    IntentHandler.message(
                            context,
                            templates.get(
                                    "thanks"
                            ),
                            suggestions(
                                    role
                            )
                    );

            case GOODBYE ->
                    IntentHandler.message(
                            context,
                            templates.get(
                                    "goodbye"
                            ),
                            List.of()
                    );

            case HELP ->
                    IntentHandler.message(
                            context,
                            templates.first(
                                    helpKey(
                                            role
                                    ),
                                    Map.of()
                            ),
                            suggestions(
                                    role
                            )
                    );

            default ->
                    IntentHandler.message(
                            context,
                            templates.first(
                                    "fallback",
                                    Map.of()
                            ),
                            suggestions(
                                    role
                            )
                    );
        };
    }

    private static String helpKey(
            RoleCode role
    ) {
        if (role == RoleCode.MEMBER) {
            return "help.member";
        }

        if (role == RoleCode.BRANCH_MANAGER) {
            return "help.manager";
        }

        return "help.admin";
    }

    private List<ChatSuggestion> suggestions(
            RoleCode role
    ) {
        return policy.suggestionsFor(role)
                .stream()
                .map(
                        ChatSuggestion::of
                )
                .toList();
    }
}