package com.gymfit.chat.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.user.RoleCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nạp kho FAQ {@code chatbot/faq_kb.json} (V2-3, plan 4.5 / 5.3).
 *
 * <p>File là một mảng các mục; {@code id} phải duy nhất; thiếu {@code source}
 * hoặc có ít hơn 6 câu hỏi thì bị <b>bỏ qua</b> (plan 4.5: "Mục nào chưa xác
 * minh được → không thêm").
 */
@Slf4j
@Component
public class FaqKnowledgeBase {

    public static final String RESOURCE =
            "chatbot/faq_kb.json";

    /**
     * File câu hỏi bổ sung: {@code { "questions": { "<id>": ["...", ...] } }}.
     *
     * <p>Cách làm này để kho FAQ mở rộng vốn từ (mỗi mục thêm 4 cách hỏi) mà
     * không phải gộp mọi thứ vào một file {@link #RESOURCE} quá lớn. Câu hỏi ở
     * đây <b>không</b> mang thông tin sự thật - {@code source}/{@code answer}
     * vẫn lấy từ {@link #RESOURCE}.
     */
    public static final String EXTRA_QUESTIONS_RESOURCE =
            "chatbot/faq_questions_extra.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    /** Số câu hỏi tối thiểu cho một mục (plan 4.5). */
    public static final int MIN_QUESTIONS =
            6;

    private final List<FaqEntry> entries =
            new ArrayList<>();

    private final Map<String, FaqEntry> byId =
            new LinkedHashMap<>();

    /** Nạp (hoặc nạp lại) toàn bộ kho từ classpath. */
    public void load() {

        entries.clear();
        byId.clear();

        try (InputStream input =
                     new ClassPathResource(
                             RESOURCE
                     ).getInputStream()) {

            JsonNode root =
                    MAPPER.readTree(input);

            if (!root.isArray()) {

                log.warn(
                        "{} phải là mảng mục FAQ",
                        RESOURCE
                );

                return;
            }

            for (JsonNode node : root) {

                FaqEntry entry =
                        parse(node);

                if (entry == null) {
                    continue;
                }

                if (byId.containsKey(entry.id())) {

                    log.warn(
                            "FAQ id trùng: {} - bỏ qua",
                            entry.id()
                    );

                    continue;
                }

                entries.add(entry);
                byId.put(entry.id(), entry);
            }

        } catch (Exception exception) {

            log.warn(
                    "Không đọc được {}: {}",
                    RESOURCE,
                    exception.getMessage()
            );
        }

        mergeExtraQuestions();

        log.info(
                "Kho FAQ: {} mục từ {}",
                entries.size(),
                RESOURCE
        );
    }

    /**
     * Gộp {@link #EXTRA_QUESTIONS_RESOURCE} vào các mục đã nạp.
     *
     * <p>Chỉ bổ sung câu hỏi; id lạ trong file bổ sung bị bỏ qua kèm cảnh báo
     * (không phá server). Lỗi đọc file không làm mất kho chính.
     */
    private void mergeExtraQuestions() {

        try (InputStream input =
                     new ClassPathResource(
                             EXTRA_QUESTIONS_RESOURCE
                     ).getInputStream()) {

            JsonNode root =
                    MAPPER.readTree(input);

            JsonNode questions =
                    root.path("questions");

            if (!questions.isObject()) {

                log.warn(
                        "{} phải là { \"questions\": { ... } }",
                        EXTRA_QUESTIONS_RESOURCE
                );

                return;
            }

            int added =
                    0;

            java.util.Iterator<Map.Entry<String, JsonNode>> fields =
                    questions.fields();

            while (fields.hasNext()) {

                Map.Entry<String, JsonNode> field =
                        fields.next();

                FaqEntry entry =
                        byId.get(field.getKey());

                if (entry == null) {

                    log.warn(
                            "{} có id lạ '{}' - bỏ qua",
                            EXTRA_QUESTIONS_RESOURCE,
                            field.getKey()
                    );

                    continue;
                }

                List<String> merged =
                        new ArrayList<>(entry.questions());

                for (String question : strings(field.getValue())) {

                    if (!merged.contains(question)) {

                        merged.add(question);
                        added++;
                    }
                }

                if (merged.size() == entry.questions().size()) {
                    continue;
                }

                FaqEntry updated =
                        new FaqEntry(
                                entry.id(),
                                entry.roles(),
                                entry.status(),
                                List.copyOf(merged),
                                entry.answer(),
                                entry.link(),
                                entry.source(),
                                entry.tags()
                        );

                entries.set(
                        entries.indexOf(entry),
                        updated
                );

                byId.put(
                        updated.id(),
                        updated
                );
            }

            log.info(
                    "FAQ: bổ sung {} câu hỏi từ {}",
                    added,
                    EXTRA_QUESTIONS_RESOURCE
            );

        } catch (Exception exception) {

            log.warn(
                    "Không đọc được {}: {}",
                    EXTRA_QUESTIONS_RESOURCE,
                    exception.getMessage()
            );
        }
    }

    /** Giải nén 1 mục; trả {@code null} nếu thiếu bản sắc bắt buộc. */
    private static FaqEntry parse(
            JsonNode node
    ) {

        String id =
                node.path("id").asText("");

        String source =
                node.path("source").asText("");

        List<String> questions =
                strings(node.path("questions"));

        if (id.isBlank()
                || source.isBlank()
                || questions.size() < MIN_QUESTIONS) {

            log.warn(
                    "Bỏ qua mục FAQ '{}' (id={}, source={}, {} câu) - "
                            + "bắt buộc id + source + ≥ {} câu hỏi",
                    id,
                    node.path("id").asText(""),
                    source.isBlank() ? "THIẾU" : source,
                    questions.size(),
                    MIN_QUESTIONS
            );

            return null;
        }

        return new FaqEntry(
                id,
                roles(node.path("roles")),
                node.path("status")
                        .asText(FaqEntry.STATUS_ACTIVE),
                questions,
                node.path("answer").asText(""),
                node.path("link").asText(""),
                source,
                strings(node.path("tags"))
        );
    }

    private static List<String> strings(
            JsonNode node
    ) {

        List<String> values =
                new ArrayList<>();

        if (node != null && node.isArray()) {

            for (JsonNode item : node) {

                String value =
                        item.asText("");

                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }

        return values;
    }

    private static java.util.Set<RoleCode> roles(
            JsonNode node
    ) {

        java.util.Set<RoleCode> roles =
                java.util.EnumSet.noneOf(RoleCode.class);

        if (node != null && node.isArray()) {

            for (JsonNode item : node) {

                try {

                    roles.add(
                            RoleCode.valueOf(
                                    item.asText()
                            )
                    );

                } catch (IllegalArgumentException ignored) {
                    // vai trò lạ trong file - bỏ qua, không phá server
                }
            }
        }

        return roles;
    }

    /** Toàn bộ mục (kể cả {@code PENDING_FEATURE} - retriever tự lọc). */
    public List<FaqEntry> entries() {
        return List.copyOf(entries);
    }

    /** Tra mục theo id; {@code null} nếu không có. */
    public FaqEntry byId(
            String id
    ) {
        return byId.get(id);
    }

    public int size() {
        return entries.size();
    }
}
