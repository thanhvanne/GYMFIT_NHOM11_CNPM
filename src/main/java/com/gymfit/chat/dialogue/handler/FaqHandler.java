package com.gymfit.chat.dialogue.handler;

import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.nlu.Intent;
import com.gymfit.chat.nlg.ResponseTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 6 intent FAQ — nội dung tĩnh trong {@code faq.json}, không gọi service.
 */
@Component
@RequiredArgsConstructor
public class FaqHandler
        implements IntentHandler {

    private final ResponseTemplates templates;

    @Override
    public Set<Intent> supports() {
        return EnumSet.of(
                Intent.FAQ_CANCEL_POLICY,
                Intent.FAQ_BOOKING_RULES,
                Intent.FAQ_CHECKIN_HOWTO,
                Intent.FAQ_BUY_PLAN_HOWTO,
                Intent.FAQ_CHECKIN_REJECTED,
                Intent.FAQ_QR_HOWTO
        );
    }

    @Override
    public ChatResponse handle(
            HandlerContext context
    ) {
        return IntentHandler.message(
                context,
                templates.faqAnswer(
                        context.intent()
                                .name()
                )
        );
    }
}