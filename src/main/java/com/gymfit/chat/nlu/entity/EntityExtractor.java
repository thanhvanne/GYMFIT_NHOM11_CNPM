package com.gymfit.chat.nlu.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.branch.ServiceCode;
import com.gymfit.chat.nlu.DateTimeParser;
import com.gymfit.chat.nlu.NormalizedText;
import com.gymfit.chat.nlu.TextNormalizer;
import com.gymfit.plan.PlanTier;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Trích entity từ câu nói.
 *
 * <p>Quy tắc khớp: <b>nguyên từ / nguyên cụm</b>, ưu tiên cụm dài nhất,
 * các span không được chồng lấn.
 *
 * <p>{@code today} luôn được truyền từ bên ngoài (không gọi {@code LocalDate.now()} bên trong)
 * để test ổn định.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityExtractor {

    public static final String SYNONYMS =
            "chatbot/synonyms.json";

    private final GazetteerProvider gazetteer;
    private final DateTimeParser dateTimeParser;

    /**
     * Từ điển tĩnh: tên canonical → alias đã bỏ dấu, đã sắp (cụm dài nhất trước).
     */
    private List<Alias> services = List.of();

    private List<Alias> tiers = List.of();

    @PostConstruct
    public void load() {
        this.services =
                readAliases("service");

        this.tiers =
                readAliases("tier");
    }

    public Entities extract(
            NormalizedText text,
            LocalDate today
    ) {
        if (text == null
                || text.plain() == null
                || text.plain()
                        .isBlank()
                || today == null) {
            return empty();
        }

        String plain =
                text.plain();

        List<Span> spans =
                new ArrayList<>();

        Set<ServiceCode> serviceCodes =
                new LinkedHashSet<>();

        matchStatic(
                plain,
                services,
                spans,
                EntityType.SERVICE,
                alias ->
                        parseEnum(
                                alias,
                                ServiceCode.class
                        )
                                .ifPresent(
                                        serviceCodes::add
                                )
        );

        Long branchId = null;
        String productSku = null;

        Set<PlanTier> tierCodes =
                new LinkedHashSet<>();

        matchStatic(
                plain,
                tiers,
                spans,
                EntityType.TIER,
                alias ->
                        parseEnum(
                                alias,
                                PlanTier.class
                        )
                                .ifPresent(
                                        tierCodes::add
                                )
        );

        String branchRaw =
                matchDynamic(
                        plain,
                        spans,
                        EntityType.BRANCH,
                        gazetteer.branchAliases()
                                .entrySet()
                                .stream()
                                .map(entry ->
                                        Map.entry(
                                                entry.getKey(),
                                                String.valueOf(
                                                        entry.getValue()
                                                )
                                        )
                                )
                                .toList()
                );

        if (branchRaw != null) {
            branchId =
                    Long.valueOf(branchRaw);
        }

        productSku =
                matchDynamic(
                        plain,
                        spans,
                        EntityType.PRODUCT,
                        gazetteer.productAliases()
                                .entrySet()
                );

        DateTimeParser.Result temporal =
                dateTimeParser.parse(
                        plain,
                        today
                );

        for (Span span : temporal.spans()) {

            if (!overlaps(
                    spans,
                    span.start(),
                    span.end()
            )) {
                spans.add(span);
            }
        }

        spans.sort(
                Comparator.comparingInt(Span::start)
        );

        return new Entities(
                List.copyOf(spans),
                Set.copyOf(serviceCodes),
                branchId,
                temporal.date(),
                temporal.time(),
                tierCodes.isEmpty()
                        ? null
                        : tierCodes.iterator()
                        .next(),
                temporal.durationDays(),
                temporal.money(),
                temporal.bookingCode(),
                productSku,
                temporal.rangeFrom(),
                temporal.rangeTo()
        );
    }

    private static Entities empty() {
        return new Entities(
                List.of(),
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    // ------------------------------------------------------------------
    // Khớp
    // ------------------------------------------------------------------

    private void matchStatic(
            String plain,
            List<Alias> dictionary,
            List<Span> spans,
            EntityType type,
            Consumer<String> onMatch
    ) {
        for (Alias alias : dictionary) {

            for (String phrase : alias.phrases()) {

                int[] range =
                        findWholeWord(
                                plain,
                                phrase
                        );

                if (range == null) {
                    continue;
                }

                if (overlaps(
                        spans,
                        range[0],
                        range[1]
                )) {
                    continue;
                }

                spans.add(
                        new Span(
                                type,
                                range[0],
                                range[1],
                                plain.substring(
                                        range[0],
                                        range[1]
                                )
                        )
                );

                onMatch.accept(
                        alias.canonical()
                );

                break;
            }
        }
    }

    /**
     * Khớp alias động (chi nhánh / sản phẩm) và ghi thẳng vào {@code spans} chung
     * để không đè lên span của từ điển tĩnh.
     */
    private String matchDynamic(
            String plain,
            List<Span> spans,
            EntityType type,
            Collection<Map.Entry<String, String>> aliases
    ) {
        List<Map.Entry<String, String>> sorted =
                new ArrayList<>(aliases);

        sorted.sort(
                Comparator.comparingInt(
                                (Map.Entry<String, String> entry) ->
                                        entry.getKey()
                                                .length()
                                )
                        .reversed()
        );

        String value =
                null;

        for (Map.Entry<String, String> entry : sorted) {

            if (value != null) {
                break;
            }

            int[] range =
                    findWholeWord(
                            plain,
                            entry.getKey()
                    );

            if (range == null) {
                continue;
            }

            if (overlaps(
                    spans,
                    range[0],
                    range[1]
            )) {
                continue;
            }

            spans.add(
                    new Span(
                            type,
                            range[0],
                            range[1],
                            entry.getKey()
                    )
            );

            value =
                    entry.getValue();
        }

        return value;
    }

    private static boolean overlaps(
            List<Span> spans,
            int start,
            int end
    ) {
        for (Span span : spans) {
            if (start < span.end()
                    && span.start() < end) {
                return true;
            }
        }

        return false;
    }

    private static <E extends Enum<E>> java.util.Optional<E> parseEnum(
            String name,
            Class<E> type
    ) {
        try {
            return java.util.Optional.of(
                    Enum.valueOf(
                            type,
                            name
                    )
            );
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Tìm cụm từ nguyên từ: ký tự liền trước/sau không được là chữ hoặc số.
     *
     * @return {@code {start, end}} hoặc null
     */
    private static int[] findWholeWord(
            String text,
            String phrase
    ) {
        if (phrase == null
                || phrase.isBlank()) {
            return null;
        }

        String lower =
                text.toLowerCase(Locale.ROOT);

        int from =
                0;

        while (from <= lower.length()) {

            int index =
                    lower.indexOf(
                            phrase,
                            from
                    );

            if (index < 0) {
                return null;
            }

            int end =
                    index + phrase.length();

            boolean leftOk =
                    index == 0
                            || !isWordChar(
                            lower.charAt(index - 1)
                    );

            boolean rightOk =
                    end == lower.length()
                            || !isWordChar(
                            lower.charAt(end)
                    );

            if (leftOk && rightOk) {
                return new int[]{
                        index,
                        end
                };
            }

            from =
                    index + 1;
        }

        return null;
    }

    private static boolean isWordChar(
            char c
    ) {
        return Character.isLetterOrDigit(c)
                || c == '_';
    }

    // ------------------------------------------------------------------
    // Nạp từ điển tĩnh
    // ------------------------------------------------------------------

    private record Alias(
            String canonical,
            List<String> phrases
    ) {
    }

    private static List<Alias> readAliases(
            String section
    ) {
        Map<String, List<String>> raw =
                readSection(section);

        List<Alias> aliases =
                new ArrayList<>();

        raw.forEach((canonical, phrases) ->
                aliases.add(
                        new Alias(
                                canonical,
                                phrases
                        )
                )
        );

        // Nhóm có cụm dài nhất đứng trước để ưu tiên "tap gym" hơn "gym".
        aliases.sort(
                Comparator.comparingInt(
                                (Alias alias) ->
                                        alias.phrases()
                                                .stream()
                                                .mapToInt(String::length)
                                                .max()
                                                .orElse(0)
                                )
                        .reversed()
        );

        return List.copyOf(aliases);
    }

    private static Map<String, List<String>> readSection(
            String section
    ) {
        Map<String, List<String>> result =
                new LinkedHashMap<>();

        try (InputStream input =
                     new ClassPathResource(
                             SYNONYMS
                     ).getInputStream()) {

            JsonNode root =
                    new ObjectMapper()
                            .readTree(input);

            JsonNode node =
                    root.path(section);

            if (!node.isObject()) {
                return result;
            }

            node.fields()
                    .forEachRemaining(entry -> {

                        List<String> phrases =
                                new ArrayList<>();

                        entry.getValue()
                                .forEach(value -> {

                                    String plain =
                                            TextNormalizer.toPlain(
                                                    value.asText("")
                                            )
                                                    .trim();

                                    if (!plain.isBlank()
                                            && !phrases.contains(plain)) {
                                        phrases.add(plain);
                                    }
                                });

                        phrases.sort(
                                Comparator.comparingInt(
                                                String::length
                                        )
                                        .reversed()
                        );

                        result.put(
                                entry.getKey(),
                                List.copyOf(phrases)
                        );
                    });

        } catch (Exception exception) {

            log.warn(
                    "Không đọc được từ điển \"{}\": {}",
                    section,
                    exception.getMessage()
            );
        }

        return result;
    }

}