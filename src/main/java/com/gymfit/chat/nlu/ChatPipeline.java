package com.gymfit.chat.nlu;

import com.gymfit.chat.nlu.entity.Entities;
import com.gymfit.chat.nlu.entity.EntityExtractor;
import com.gymfit.chat.nlu.entity.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Pipeline xử lý câu nói — <b>dùng chung</b> cho cả huấn luyện lẫn dự đoán.
 *
 * <pre>
 * raw → TextNormalizer.plain → EntityExtractor → MASK
 * </pre>
 *
 * <p>Nếu train và predict dùng hai đường khác nhau thì điểm test rất cao nhưng
 * chạy thật sai. Mọi lời gọi đều phải đi qua đây.
 */
@Component
@RequiredArgsConstructor
public class ChatPipeline {

    private static final String TOKEN_DATE =
            "<date>";

    private static final String TOKEN_TIME =
            "<time>";

    private static final String TOKEN_MONEY =
            "<money>";

    private static final String TOKEN_CODE =
            "<code>";

    private final TextNormalizer normalizer;
    private final EntityExtractor entityExtractor;

    public NormalizedText normalize(
            String raw
    ) {
        return normalizer.normalize(raw);
    }

    public Entities extract(
            NormalizedText text,
            LocalDate today
    ) {
        return entityExtractor.extract(
                text,
                today
        );
    }

    /**
     * Thay span thời gian / tiền / mã bằng placeholder.
     * Giữ nguyên SERVICE / BRANCH / TIER vì chúng mang tín hiệu.
     */
    public String mask(
            String plain,
            Entities entities
    ) {
        if (plain == null
                || plain.isBlank()) {
            return "";
        }

        String masked =
                plain;

        List<Span> spans =
                entities == null
                        ? List.of()
                        : entities.spans();

        // Thay từ cuối về đầu để không làm lệch chỉ số.
        List<Span> ordered =
                spans.stream()
                        .sorted(
                                Comparator.comparingInt(
                                                Span::start
                                        )
                                        .reversed()
                        )
                        .toList();

        for (Span span : ordered) {

            String token =
                    switch (span.type()) {
                        case DATE ->
                                TOKEN_DATE;
                        case TIME ->
                                TOKEN_TIME;
                        case MONEY ->
                                TOKEN_MONEY;
                        case BOOKING_CODE ->
                                TOKEN_CODE;
                        default ->
                                null;
                    };

            if (token == null) {
                continue;
            }

            if (span.start() < 0
                    || span.end() > masked.length()
                    || span.start() >= span.end()) {
                continue;
            }

            masked =
                    masked.substring(
                            0,
                            span.start()
                    )
                            + token
                            + masked.substring(
                            span.end()
                    );
        }

        return masked;
    }

    /**
     * Chuỗi đầy đủ đưa vào mô hình: chuẩn hóa → trích entity → mask.
     */
    public String prepare(
            String raw,
            LocalDate today
    ) {
        return prepare(
                normalize(raw),
                today
        );
    }

    public String prepare(
            NormalizedText text,
            LocalDate today
    ) {
        return mask(
                text.plain(),
                extract(
                        text,
                        today
                )
        );
    }

}