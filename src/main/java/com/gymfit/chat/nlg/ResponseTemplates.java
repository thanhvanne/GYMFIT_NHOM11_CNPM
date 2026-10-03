package com.gymfit.chat.nlg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.common.error.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Câu trả lời tiếng Việt lấy từ {@code chatbot/templates.vi.json}.
 *
 * <p>Giá trị là <b>mảng</b> → chọn ngẫu nhiên (greeting/thanks/…). Giá trị là
 * chuỗi → cố định. Thiếu biến {@code {x}} trong template → ném lỗi rõ ràng để
 * phát hiện sớm khi ai đó quên truyền biến.
 */
@Component
@Slf4j
public class ResponseTemplates {

    public static final String TEMPLATES =
            "chatbot/templates.vi.json";

    public static final String FAQ =
            "chatbot/faq.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private final Map<String, List<String>> templates =
            new ConcurrentHashMap<>();

    private final Map<String, FaqEntry> faq =
            new ConcurrentHashMap<>();

    private final Random random =
            new Random(
                    7L
            );

    /** Một mục FAQ. */
    public record FaqEntry(
            String answer,
            String link
    ) {
    }

    @PostConstruct
    public void load() {
        templates.clear();

        faq.clear();

        readTemplates();

        readFaq();
    }

    private void readTemplates() {
        try (InputStream input =
                     new ClassPathResource(
                             TEMPLATES
                     ).getInputStream()) {

            JsonNode root =
                    MAPPER.readTree(input);

            root.fields()
                    .forEachRemaining(entry ->
                            templates.put(
                                    entry.getKey(),
                                    variants(
                                            entry.getValue()
                                    )
                            )
                    );

            log.info(
                    "Đã nạp {} nhóm câu trả lời",
                    templates.size()
            );

        } catch (Exception exception) {

            log.error(
                    "Không đọc được {}: {}",
                    TEMPLATES,
                    exception.getMessage()
            );

            throw new IllegalStateException(
                    "Không đọc được " + TEMPLATES,
                    exception
            );
        }
    }

    private void readFaq() {
        try (InputStream input =
                     new ClassPathResource(
                             FAQ
                     ).getInputStream()) {

            JsonNode root =
                    MAPPER.readTree(input);

            root.fields()
                    .forEachRemaining(entry ->
                            faq.put(
                                    entry.getKey(),
                                    new FaqEntry(
                                            entry.getValue()
                                                    .path("answer")
                                                    .asText(""),
                                            entry.getValue()
                                                    .path("link")
                                                    .asText("")
                                    )
                            )
                    );

        } catch (Exception exception) {

            log.warn(
                    "Không đọc được {}: {}",
                    FAQ,
                    exception.getMessage()
            );
        }
    }

    private static List<String> variants(
            JsonNode node
    ) {
        List<String> result =
                new ArrayList<>();

        if (node.isArray()) {
            node.forEach(value ->
                    result.add(
                            value.asText("")
                    )
            );

        } else {
            result.add(
                    node.asText("")
            );
        }

        return List.copyOf(result);
    }

    /**
     * Lấy câu trả lời theo khóa, thay {@code {var}}.
     *
     * @throws IllegalArgumentException nếu thiếu biến hoặc khóa không tồn tại
     */
    public String get(
            String key,
            Map<String, Object> vars
    ) {
        List<String> options =
                templates.get(key);

        if (options == null
                || options.isEmpty()) {
            throw new IllegalArgumentException(
                    "Không tìm thấy template: " + key
            );
        }

        String template =
                options.get(
                        random.nextInt(
                                options.size()
                        )
                );

        return render(
                template,
                vars,
                key
        );
    }

    /**
     * Lấy câu trả lời không có biến.
     */
    public String get(
            String key
    ) {
        return get(
                key,
                Map.of()
        );
    }

    /**
     * Lấy câu trả lời bằng đúng biến đầu tiên (bỏ qua tính ngẫu nhiên).
     */
    public String first(
            String key,
            Map<String, Object> vars
    ) {
        List<String> options =
                templates.get(key);

        if (options == null
                || options.isEmpty()) {
            throw new IllegalArgumentException(
                    "Không tìm thấy template: " + key
            );
        }

        return render(
                options.get(0),
                vars,
                key
        );
    }

    public boolean has(
            String key
    ) {
        List<String> options =
                templates.get(key);

        return options != null
                && !options.isEmpty();
    }

    private String render(
            String template,
            Map<String, Object> vars,
            String key
    ) {

        if (vars == null
                || vars.isEmpty()) {

            if (template.contains(
                    "{"
            )) {
                throw new IllegalArgumentException(
                        "Template " + key
                                + " còn biến chưa thay: "
                                + template
                );
            }

            return template;
        }

        String result =
                template;

        for (Map.Entry<String, Object> entry
                : vars.entrySet()) {

            result =
                    result.replace(
                            "{" + entry.getKey() + "}",
                            value(
                                    entry.getValue()
                            )
                    );
        }

        if (result.contains(
                "{"
        )) {
            throw new IllegalArgumentException(
                    "Template " + key
                            + " còn biến chưa thay: "
                            + result
            );
        }

        return result;
    }

    private static String value(
            Object value
    ) {
        return value == null
                ? ""
                : String.valueOf(value);
    }

    // ------------------------------------------------------------------
    // FAQ
    // ------------------------------------------------------------------

    public FaqEntry faq(
            String key
    ) {
        return faq.get(key);
    }

    /**
     * Câu trả lời FAQ kèm link trang liên quan.
     */
    public String faqAnswer(
            String key
    ) {
        FaqEntry entry =
                faq.get(key);

        if (entry == null) {
            return get(
                    "info.no_data"
            );
        }

        return get(
                "faq",
                Map.of(
                        "answer",
                        entry.answer(),
                        "link",
                        entry.link()
                )
        );
    }

    /**
     * Bắt lỗi service để trả câu thân thiện — không bao giờ lộ stack trace.
     */
    public String fromException(
            ApiException exception
    ) {
        String message =
                exception.getMessage();

        if (message == null
                || message.isBlank()) {
            return get(
                    "error.generic"
            );
        }

        return message;
    }

    public int size() {
        return templates.size();
    }

    public int faqSize() {
        return faq.size();
    }

}