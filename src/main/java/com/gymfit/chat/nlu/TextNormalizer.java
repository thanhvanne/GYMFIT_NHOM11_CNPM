package com.gymfit.chat.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa văn bản người dùng gõ về dạng "plain" ổn định để NLU xử lý.
 *
 * Quy trình (đúng thứ tự):
 * <ol>
 *     <li>NFC + trim + gộp khoảng trắng + chuyển chữ thường</li>
 *     <li>Sửa teencode theo <b>nguyên từ</b> (khớp cả dạng có dấu và không dấu)</li>
 *     <li>Bỏ dấu tiếng Việt</li>
 *     <li>Lọc ký tự ngoài bảng chấp nhận + rút gọn ký tự lặp quá 2 lần</li>
 * </ol>
 *
 * Hàm {@link #normalize(String)} là <b>idempotent</b>: chạy lại trên {@code plain}
 * cho ra kết quả không đổi.
 */
@Component
@Slf4j
public class TextNormalizer {

    public static final String RESOURCE = "chatbot/synonyms.json";

    /**
     * Ký tự được giữ lại sau bước lọc.
     * {@code _} là bắt buộc vì mã nghiệp vụ có dạng {@code BOOK_0123ABCD}.
     */
    private static final Pattern KEEP =
            Pattern.compile("[^a-z0-9 :/\\-.,?_]");

    /**
 * Ký tự chữ lặp từ 3 lần trỡ lên → rút còn 2.
 * <p>Chỉ áp dụng cho <b>chữ</b>: rút gọn chữ số sẽ phá hỏng số tiền và ngày
 * ("600000" → "600", "500.000" → "500.00").
 */
    private static final Pattern REPEATED =
            Pattern.compile("([a-z])\\1{2,}");

    private static final Pattern SPACES =
            Pattern.compile("\\s+");

    private Map<String, String> teencode = Map.of();

    @PostConstruct
    public void load() {
        this.teencode = loadTeencode(
                RESOURCE
        );
    }

    public NormalizedText normalize(
            String raw
    ) {
        if (raw == null) {
            return new NormalizedText(
                    "",
                    ""
            );
        }

        String plain =
                applyRepeats(
                        applyCharFilter(
                                stripDiacritics(
                                        applyTeencode(
                                                basic(raw)
                                        )
                                )
                        )
                );

        return new NormalizedText(
                raw.trim(),
                plain
        );
    }

    /**
     * Chuẩn hóa xong rồi bỏ dấu — dùng để khớp từ điển tĩnh khi tiền xử lý
     * dữ liệu huấn luyện.
     */
    public static String toPlain(
            String text
    ) {
        if (text == null) {
            return "";
        }

        return applyRepeats(
                applyCharFilter(
                        stripDiacritics(
                                basic(text)
                        )
                )
        );
    }

    private static String basic(
            String raw
    ) {
        return SPACES.matcher(
                        Normalizer.normalize(
                                        raw,
                                        Form.NFC
                                )
                                .trim()
                                .toLowerCase(Locale.ROOT)
                )
                .replaceAll(" ");
    }

    private String applyTeencode(
            String text
    ) {
        String[] tokens =
                text.split(" ");

        StringBuilder builder =
                new StringBuilder();

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            String replacement =
                    lookup(token);

            if (replacement == null) {
                append(
                        builder,
                        token
                );

                continue;
            }

            // Teencode bị xoá hoàn toàn (ví dụ "ạ", "ak").
            if (!replacement.isEmpty()) {
                append(
                        builder,
                        replacement
                );
            }
        }

        return builder.toString();
    }

    private String lookup(
            String token
    ) {
        String replacement =
                teencode.get(
                        token.toLowerCase(Locale.ROOT)
                );

        if (replacement != null) {
            return replacement;
        }

        // Người dùng có thể gõ không dấu ("dc") trong khi bảng ghi có dấu ("đc").
        return teencode.get(
                stripDiacritics(
                        token.toLowerCase(Locale.ROOT)
                )
        );
    }

    private static void append(
            StringBuilder builder,
            String value
    ) {
        if (value.isBlank()) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append(' ');
        }

        builder.append(value);
    }

    /**
     * Bỏ dấu tiếng Việt. Dùng NFD để tách dấu rồi xoá, riêng chữ {@code đ}
     * không tách được nên thay thẳng.
     */
    public static String stripDiacritics(
            String text
    ) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String stripped =
                Normalizer.normalize(
                                text,
                                Form.NFD
                        )
                        .replaceAll("\\p{M}+", "")
                        .replace('đ', 'd')
                        .replace('Đ', 'D');

        return Normalizer.normalize(
                stripped,
                Form.NFC
        );
    }

    private static String applyCharFilter(
            String text
    ) {
        return SPACES.matcher(
                        KEEP.matcher(
                                        text.toLowerCase(Locale.ROOT)
                                )
                                .replaceAll(" ")
                )
                .replaceAll(" ")
                .trim();
    }

    private static String applyRepeats(
            String text
    ) {
        return REPEATED.matcher(text)
                .replaceAll("$1$1");
    }

    /**
     * Nạp bảng teencode. Mỗi khoá được lưu cả dạng gốc lẫn dạng đã bỏ dấu để
     * khớp được cả hai kiểu gõ.
     */
    static Map<String, String> loadTeencode(
            String classpathResource
    ) {
        Map<String, String> table =
                new LinkedHashMap<>();

        try (InputStream input =
                     new ClassPathResource(
                             classpathResource
                     ).getInputStream()) {

            JsonNode root =
                    new ObjectMapper()
                            .readTree(input);

            JsonNode section =
                    root.path("teencode");

            if (!section.isObject()) {
                return Map.of();
            }

            section.fields()
                    .forEachRemaining(entry -> {

                        String key =
                                entry.getKey()
                                        .trim()
                                        .toLowerCase(Locale.ROOT);

                        String value =
                                entry.getValue()
                                        .asText("");

                        if (key.isEmpty()) {
                            return;
                        }

                        table.put(
                                key,
                                value
                        );

                        String plainKey =
                                stripDiacritics(key);

                        if (!plainKey.equals(key)) {
                            table.putIfAbsent(
                                    plainKey,
                                    value
                            );
                        }
                    });

        } catch (Exception exception) {

            log.error(
                    "Không đọc được bảng teencode từ {}: {}",
                    classpathResource,
                    exception.getMessage()
            );

            return Map.of();
        }

        return Map.copyOf(table);
    }

}