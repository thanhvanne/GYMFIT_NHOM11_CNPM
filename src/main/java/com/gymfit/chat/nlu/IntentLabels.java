package com.gymfit.chat.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.dialogue.IntentPolicy;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Mô tả tiếng Việt cho từng intent, đọc từ {@code chatbot/intents.json}.
 * <p>Dùng cho câu hỏi làm rõ và câu từ chối.
 */
public final class IntentLabels {

    private static final Map<Intent, String> DESCRIPTIONS =
            new EnumMap<>(Intent.class);

    static {
        load();
    }

    private IntentLabels() {
    }

    public static String describe(
            Intent intent
    ) {
        if (intent == null) {
            return "";
        }

        return DESCRIPTIONS.getOrDefault(
                intent,
                intent.name()
        );
    }

    private static void load() {

        try (InputStream input =
                     IntentLabels.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     IntentPolicy.RESOURCE
                             )) {

            if (input == null) {
                throw new IllegalStateException(
                        "Không tìm thấy "
                                + IntentPolicy.RESOURCE
                );
            }

            JsonNode root =
                    new ObjectMapper()
                            .readTree(input);

            root.fields()
                    .forEachRemaining(entry -> {

                        try {

                            DESCRIPTIONS.put(
                                    Intent.valueOf(
                                            entry.getKey()
                                    ),
                                    entry.getValue()
                                            .path("desc")
                                            .asText(
                                                    entry.getKey()
                                            )
                            );

                        } catch (IllegalArgumentException ignored) {
                            // intent trong file không khớp enum → bỏ qua
                        }
                    });

        } catch (Exception exception) {

            for (Intent intent : Intent.values()) {
                DESCRIPTIONS.put(
                        intent,
                        intent.name()
                );
            }
        }
    }

}