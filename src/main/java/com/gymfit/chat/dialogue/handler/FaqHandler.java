package com.gymfit.chat.dialogue.handler;

import com.gymfit.chat.dto.ChatCard;
import com.gymfit.chat.dto.ChatCardLine;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.dto.ChatSuggestion;
import com.gymfit.chat.knowledge.FaqEntry;
import com.gymfit.chat.knowledge.FaqRetriever;
import com.gymfit.chat.nlg.ResponseTemplates;
import com.gymfit.chat.nlu.Intent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kho FAQ (V2-3, plan 5.3).
 *
 * <p>Với {@link Intent#FAQ_GENERAL} thì truy vấn {@link FaqRetriever} bằng đúng
 * câu người dùng gõ:
 *
 * <ul>
 *     <li>điểm ≥ {@link FaqRetriever#THRESHOLD_ANSWER} → trả lời + thẻ có
 *     {@code link};</li>
 *     <li>{@code [THRESHOLD_SUGGEST, THRESHOLD_ANSWER)} → chip
 *     "Có phải bạn muốn hỏi:";</li>
 *     <li>&lt; {@link FaqRetriever#THRESHOLD_SUGGEST} → câu trả lời "chưa
 *     tìm thấy" (không bịa nội dung).</li>
 * </ul>
 *
 * <p>Các intent FAQ <b>cũ</b> đã {@code @Deprecated} vẫn trả lời tĩnh theo
 * {@code faq.json} để không vỡ hành vi với nhãn cũ còn trong DB/holdout; về mô
 * hình chúng đã được gộp vào {@code FAQ_GENERAL} qua
 * {@code chatbot/intent-aliases.json}.
 */
@Component
@RequiredArgsConstructor
public class FaqHandler
        implements IntentHandler {

    private final ResponseTemplates templates;

    private final FaqRetriever retriever;

    @Override
    public Set<Intent> supports() {
        return EnumSet.of(
                Intent.FAQ_GENERAL,
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

        // Đường cũ (đã deprecated): câu trả lời tĩnh theo intent.
        if (context.intent() != null
                && context.intent().isMergedFaq()) {

            return IntentHandler.message(
                    context,
                    templates.faqAnswer(
                            context.intent()
                                    .name()
                    )
            );
        }

        Reply reply =
                render(
                        templates,
                        retriever.retrieve(
                                context.raw(),
                                context.principal() == null
                                        ? null
                                        : context.principal()
                                        .getRole()
                        )
                );

        return ChatResponse.of(
                        null,
                        reply.message(),
                        context.intent() == null
                                ? null
                                : context.intent()
                                .name(),
                        null
                )
                .withCard(
                        reply.card()
                )
                .withSuggestions(
                        reply.suggestions()
                );
    }

    /** Nội dung trả lời cho một kết quả truy vấn FAQ. */
    public record Reply(
            String message,
            ChatCard card,
            List<ChatSuggestion> suggestions
    ) {
    }

    /**
     * Dựng câu trả lời từ {@link FaqRetriever.Result} - dùng chung cho
     * {@code FaqHandler} và chuỗi cứu hộ trong {@code DialogueManager}.
     */
    public static Reply render(
            ResponseTemplates templates,
            FaqRetriever.Result result
    ) {

        if (result == null || result.hit()) {

            FaqEntry entry =
                    result == null ? null : result.entry();

            if (entry == null) {

                return new Reply(
                        templates.get("faq.no_match"),
                        null,
                        List.of()
                );
            }

            boolean unsupported =
                    FaqEntry.STATUS_NOT_SUPPORTED
                            .equals(entry.status());

            String message =
                    unsupported
                            ? templates.get(
                            "faq.not_supported",
                            Map.of(
                                    "answer",
                                    entry.answer()
                            )
                    )
                            : templates.get(
                            "faq",
                            Map.of(
                                    "answer",
                                    entry.answer(),
                                    "link",
                                    entry.link()
                            )
                    );

            ChatCard card =
                    ChatCard.list(
                            entry.sampleQuestion(),
                            List.of(
                                    ChatCardLine.of(
                                            "Xem chi tiết",
                                            entry.link()
                                    ),
                                    ChatCardLine.of(
                                            "Nguồn",
                                            entry.source()
                                    )
                            )
                    );

            return new Reply(
                    message,
                    card,
                    List.of()
            );
        }

        if (result.suggest()) {

            List<ChatSuggestion> chips =
                    new ArrayList<>();

            for (FaqEntry entry : result.suggestions()) {

                chips.add(
                        ChatSuggestion.of(
                                entry.sampleQuestion()
                        )
                );
            }

            return new Reply(
                    templates.get("faq.suggest"),
                    null,
                    List.copyOf(chips)
            );
        }

        return new Reply(
                templates.get("faq.no_match"),
                null,
                List.of()
        );
    }
}
