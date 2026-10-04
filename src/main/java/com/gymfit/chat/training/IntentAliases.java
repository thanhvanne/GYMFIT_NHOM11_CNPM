package com.gymfit.chat.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bảng ánh xạ nhãn intent cũ → nhãn mới ({@code chatbot/intent-aliases.json}).
 *
 * <p>V2-3 gộp 6 intent FAQ vào {@code FAQ_GENERAL}. Dataset sinh ra và kết quả
 * đánh giá holdout đều đi qua {@link #map} nên file dữ liệu gốc giữ nguyên nhãn
 * gốc (truy vết được) nhưng mô hình/ma trận nhầm lẫn chỉ còn 1 nhãn FAQ.
 */
@Slf4j
public final class IntentAliases {

    public static final String RESOURCE =
            "chatbot/intent-aliases.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static volatile Map<String, String> aliases;

    private IntentAliases() {
    }

    /** Nạp (lazily) bảng alias từ classpath. */
    public static Map<String, String> all() {

        Map<String, String> current =
                aliases;

        if (current != null) {
            return current;
        }

        synchronized (IntentAliases.class) {

            if (aliases == null) {
                aliases = read();
            }

            return aliases;
        }
    }

    /**
     * Áp alias cho một nhãn; trả về chính nhãn đó khi không có trong bảng
     * (hoặc khi {@code label} rỗng/null).
     */
    public static String map(
            String label
    ) {

        if (label == null || label.isBlank()) {
            return label;
        }

        return all().getOrDefault(label, label);
    }

    private static Map<String, String> read() {

        Map<String, String> loaded =
                new LinkedHashMap<>();

        try (InputStream input =
                     new ClassPathResource(
                             RESOURCE
                     ).getInputStream()) {

            JsonNode root =
                    MAPPER.readTree(input);

            JsonNode node =
                    root.path("aliases");

            if (!node.isObject()) {
                node = root;
            }

            node.fields()
                    .forEachRemaining(entry -> {

                        if (entry.getKey()
                                .startsWith("_")) {
                            return;
                        }

                        loaded.put(
                                entry.getKey(),
                                entry.getValue()
                                        .asText()
                        );
                    });

        } catch (Exception exception) {

            log.warn(
                    "Không đọc được {}: {}",
                    RESOURCE,
                    exception.getMessage()
            );
        }

        log.info(
                "Intent alias: {} cặp từ {}",
                loaded.size(),
                RESOURCE
        );

        return loaded;
    }
}
