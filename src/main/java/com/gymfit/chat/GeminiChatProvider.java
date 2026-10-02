package com.gymfit.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.common.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeminiChatProvider implements ChatProvider {

    private static final int MAX_ATTEMPTS = 4;

    private final ObjectMapper objectMapper;

    @Value("${gymfit.ai.base-url}")
    private String baseUrl;

    @Value("${gymfit.ai.api-key}")
    private String apiKey;

    @Value("${gymfit.ai.model}")
    private String model;

    @Override
    public String chat(
            String systemPrompt,
            String userMessage
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException(
                    "ai_not_configured",
                    "Gemini AI chưa được cấu hình API key"
            );
        }

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        Map<String, Object> body = Map.of(
                "system_instruction",
                Map.of(
                        "parts",
                        List.of(
                                Map.of(
                                        "text",
                                        systemPrompt
                                )
                        )
                ),
                "contents",
                List.of(
                        Map.of(
                                "role",
                                "user",
                                "parts",
                                List.of(
                                        Map.of(
                                                "text",
                                                userMessage
                                        )
                                )
                        )
                ),
                "generationConfig",
                Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 800
                )
        );

        String response = callGeminiWithRetry(
                client,
                body
        );

        return parseResponse(response);
    }

    private String callGeminiWithRetry(
            RestClient client,
            Map<String, Object> body
    ) {
        for (
                int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++
        ) {
            try {
                System.out.println(
                        "Calling Gemini model="
                                + model
                                + ", attempt="
                                + attempt
                );

                return client.post()
                        .uri(uriBuilder ->
                                uriBuilder
                                        .path(
                                                "/v1beta/models/"
                                                        + model
                                                        + ":generateContent"
                                        )
                                        .queryParam(
                                                "key",
                                                apiKey
                                        )
                                        .build()
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(body)
                        .retrieve()
                        .body(String.class);

            } catch (
                    RestClientResponseException exception
            ) {
                int status =
                        exception
                                .getStatusCode()
                                .value();

                System.err.println(
                        "Gemini attempt "
                                + attempt
                                + " failed: HTTP "
                                + status
                );

                System.err.println(
                        "Gemini response: "
                                + exception
                                .getResponseBodyAsString()
                );

                boolean retryable =
                        status == 429
                                || status == 500
                                || status == 502
                                || status == 503
                                || status == 504;

                if (!retryable) {
                    throw new IllegalStateException(
                            "Gemini API lỗi HTTP "
                                    + status,
                            exception
                    );
                }

                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException(
                            "Gemini AI hiện không khả dụng sau "
                                    + MAX_ATTEMPTS
                                    + " lần thử",
                            exception
                    );
                }

                sleepBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException(
                "Không thể gọi Gemini AI"
        );
    }

    private void sleepBeforeRetry(
            int attempt
    ) {
        // 1s -> 2s -> 4s
        long delay =
                1000L
                        * (1L << (attempt - 1));

        System.out.println(
                "Retry Gemini sau "
                        + delay
                        + " ms"
        );

        try {
            Thread.sleep(delay);

        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "Gemini retry bị gián đoạn",
                    exception
            );
        }
    }

    private String parseResponse(
            String response
    ) {
        if (response == null
                || response.isBlank()) {
            throw new IllegalStateException(
                    "Gemini trả về response rỗng"
            );
        }

        try {
            JsonNode root =
                    objectMapper.readTree(
                            response
                    );

            JsonNode candidates =
                    root.path(
                            "candidates"
                    );

            if (!candidates.isArray()
                    || candidates.isEmpty()) {
                throw new IllegalStateException(
                        "Gemini không trả về candidate"
                );
            }

            JsonNode parts =
                    candidates
                            .path(0)
                            .path("content")
                            .path("parts");

            if (!parts.isArray()
                    || parts.isEmpty()) {
                throw new IllegalStateException(
                        "Gemini không trả về nội dung"
                );
            }

            StringBuilder answer =
                    new StringBuilder();

            for (JsonNode part : parts) {
                if (part.hasNonNull("text")) {
                    answer.append(
                            part.get("text")
                                    .asText()
                    );
                }
            }

            String result =
                    answer
                            .toString()
                            .trim();

            if (result.isBlank()) {
                throw new IllegalStateException(
                        "Gemini trả về nội dung rỗng"
                );
            }

            return result;

        } catch (IllegalStateException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể đọc response Gemini",
                    exception
            );
        }
    }
}