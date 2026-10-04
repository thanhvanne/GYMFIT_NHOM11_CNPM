package com.gymfit.chat.training;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Chuyển holdout V1 ({@code holdout.jsonl}) sang schema V2
 * ({@code holdout_v2.jsonl}) theo mục 7.1 của plan mở rộng.
 *
 * <p>Sơ đồ dòng V2:
 * <pre>{"text","intent","tier","tags":[...],"role","group"}</pre>
 *
 * <p>Các việc làm:
 * <ol>
 *   <li><b>Sửa nhãn</b> theo quy ước 6.4 (bảng {@link #RELABEL}).</li>
 *   <li><b>Tách câu mơ hồ</b> sang {@code ambiguous.jsonl} kèm lý do
 *       (quy ước 6.4: "không chắc → loại, ghi vào ambiguous").</li>
 *   <li>Gán {@code tier} bằng <b>quy tắc tự động</b> (xem {@link #tier}) –
 *       <u>không</u> phải chấm tay; V2-14 sẽ gán lại khi có câu của
 *       bạn cùng lớp.</li>
 *   <li>Gán {@code tags} đo được và {@code role} suy từ
 *       {@code intents.json}.</li>
 * </ol>
 *
 * <p>Chạy:
 * {@code mvn -q exec:java -Dexec.mainClass=com.gymfit.chat.training.HoldoutMigrator}
 */
public final class HoldoutMigrator {

    public static final String SOURCE =
            "src/main/resources/chatbot/holdout.jsonl";

    public static final String TARGET =
            "src/main/resources/chatbot/holdout_v2.jsonl";

    public static final String AMBIGUOUS =
            "src/main/resources/chatbot/ambiguous.jsonl";

    /** Nhãn phải đổi theo quy ước 6.4 (group → nhãn mới). */
    private static final Map<String, String> RELABEL = Map.of(
            // "chào + yêu cầu" → nhãn của YÊU CẦU (plan 6.4 dòng 1)
            "H001", "LIST_PLANS"
    );

    /** Câu không chắc → tách ra ambiguous.jsonl (plan 6.4 dòng 5). */
    private static final Map<String, String> AMBIGUOUS_REASONS = Map.of(
            "H004",
            "chào + \"có ai đó ở đây không\" – không rõ HELP hay GREETING",
            "H005",
            "câu trần thuật, không phải câu chào và không có yêu cầu",
            "H015",
            "\"cất điện thoại rồi\" – thiếu tín hiệu để gán GOODBYE",
            "H099",
            "\"nên chọn gói nào\" – cặp dễ nhầm PLAN_COMPARE / PLAN_RECOMMEND (6.5)"
    );

    /** Lỗi gõ có chủ đích (dùng cho tier ADVERSARIAL). */
    private static final Set<String> TYPO_TOKENS = Set.of(
            "cảu", "caw", "dc", "hong", "bik"
    );

    /** Câu an toàn cần nhận diện riêng (dùng cho tag {@code security}). */
    private static final List<String> SECURITY_KEYWORDS = List.of(
            "mật khẩu", "jwt", "token", "prompt", "hệ thống",
            "mã nguồn", "database", "reset", "email", "cấu hình"
    );

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private HoldoutMigrator() {
    }

    public static void main(
            String[] args
    ) throws Exception {

        List<String> lines =
                Files.readAllLines(
                        Paths.get(SOURCE),
                        StandardCharsets.UTF_8
                );

        List<String> kept =
                new ArrayList<>();

        List<String> ambiguous =
                new ArrayList<>();

        Map<String, Integer> byTier =
                new TreeMap<>();

        Map<String, Integer> byRole =
                new TreeMap<>();

        Map<String, Integer> tagCounts =
                new TreeMap<>();

        Map<String, Integer> perIntent =
                new TreeMap<>();

        for (String line : lines) {

            if (line.isBlank()) {
                continue;
            }

            Map<?, ?> raw =
                    MAPPER.readValue(
                            line,
                            Map.class
                    );

            String group =
                    String.valueOf(raw.get("group"));

            String text =
                    String.valueOf(raw.get("text"));

            String intent =
                    String.valueOf(raw.get("intent"));

            String reason =
                    AMBIGUOUS_REASONS.get(group);

            if (reason != null) {

                ambiguous.add(
                        toJsonl(
                                line,
                                reason
                        )
                );
                continue;
            }

            String relabelled =
                    RELABEL.get(group);

            String finalIntent =
                    relabelled == null
                            ? intent
                            : relabelled;

            String tier =
                    tier(text, finalIntent);

            List<String> tags =
                    tags(text, finalIntent, tier);

            String role =
                    roleOf(finalIntent);

            kept.add(
                    toJsonl(
                            text,
                            finalIntent,
                            tier,
                            tags,
                            role,
                            group
                    )
            );

            byTier.merge(tier, 1, Integer::sum);
            byRole.merge(role, 1, Integer::sum);
            perIntent.merge(finalIntent, 1, Integer::sum);

            tags.forEach(tag ->
                    tagCounts.merge(tag, 1, Integer::sum)
            );
        }

        write(Paths.get(TARGET), kept);
        write(Paths.get(AMBIGUOUS), ambiguous);

        System.out.println(
                "holdout_v2 = " + kept.size()
                        + " câu, ambiguous = " + ambiguous.size()
        );
        System.out.println("theo tier: " + byTier);
        System.out.println("theo role: " + byRole);
        System.out.println("theo tag : " + tagCounts);
        System.out.println(
                "số intent = " + perIntent.size()
                        + ", thấp nhất = "
                        + perIntent.entrySet().stream()
                        .mapToInt(Map.Entry::getValue)
                        .min()
                        .orElse(0)
        );
    }

    // ------------------------------------------------------------------
    // Quy tắc
    // ------------------------------------------------------------------

    /**
     * EASY ≤ 6 từ, không dấu "?", tối đa 1 dấu phẩy (dạng từ khóa/mẫu);
     * ADVERSARIAL = mất dấu / lỗi gõ / đầu vào toàn ký tự lạ;
     * còn lại = NATURAL (câu hỏi đầy đủ, câu dài, câu ghép).
     */
    static String tier(
            String text,
            String intent
    ) {

        if (isSymbols(text)) {
            return "ADVERSARIAL";
        }

        if (hasTypo(text)) {
            return "ADVERSARIAL";
        }

        if (!hasDiacritics(text)
                && words(text) >= 2) {
            return "ADVERSARIAL";
        }

        if (words(text) <= 6
                && !text.contains("?")
                && count(text, ',') <= 1) {
            return "EASY";
        }

        return "NATURAL";
    }

    static List<String> tags(
            String text,
            String intent,
            String tier
    ) {

        List<String> tags =
                new ArrayList<>();

        // tier là trường riêng, không lặp lại thành tag (tránh đếm 2 lần
        // khi in breakdown theo tier và theo tag).

        if (text.contains("?")) {
            tags.add("question");
        }

        if (words(text) <= 5) {
            tags.add("short");
        }

        if (words(text) >= 10) {
            tags.add("long");
        }

        if (!hasDiacritics(text)) {
            tags.add("no_diacritics");
        }

        if (isSymbols(text)) {
            tags.add("symbols");
        }

        if (hasTypo(text)) {
            tags.add("typo");
        }

        if (count(text, ',') >= 2) {
            tags.add("compound");
        }

        if ("OUT_OF_SCOPE".equals(intent)) {
            tags.add("oos");

            String lower =
                    text.toLowerCase(Locale.ROOT);

            if (SECURITY_KEYWORDS
                    .stream()
                    .anyMatch(lower::contains)) {
                tags.add("security");
            }
        }

        if ("H001".equals(
                relabelledFrom(text)
        )) {
            tags.add("greeting_wrapper");
        }

        return tags;
    }

    private static String relabelledFrom(
            String text
    ) {
        // H001 là câu "chào + yêu cầu" duy nhất được sửa nhãn.
        return text.startsWith("xin chào, tôi cần hỏi")
                ? "H001"
                : "";
    }

    /**
     * Role suy từ {@code intents.json}: intent có MEMBER thì persona là
     * hội viên; không có nhưng có BRANCH_MANAGER thì nhân viên; còn lại
     * là quản trị.
     */
    static String roleOf(
            String intent
    ) {

        Map<?, ?> intents =
                readIntents();

        Map<?, ?> entry =
                (Map<?, ?>) intents.get(intent);

        if (entry == null) {
            return "MEMBER";
        }

        Object roles =
                entry.get("roles");

        if (!(roles instanceof List<?> list)
                || list.isEmpty()) {
            return "MEMBER";
        }

        if (list.contains("MEMBER")) {
            return "MEMBER";
        }

        if (list.contains("BRANCH_MANAGER")) {
            return "STAFF";
        }

        return "ADMIN";
    }

    private static Map<?, ?> readIntents() {

        try {
            return MAPPER.readValue(
                    Files.readString(
                            Paths.get(
                                    "src/main/resources/chatbot/intents.json"
                            ),
                            StandardCharsets.UTF_8
                    ),
                    Map.class
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không đọc được intents.json",
                    exception
            );
        }
    }

    // ------------------------------------------------------------------
    // Ký tự
    // ------------------------------------------------------------------

    /**
     * "Có dấu tiếng Việt" hiểu là <b>có ít nhất 1 chữ ngoài ASCII</b>
     * (accent, {@code đ}, {@code ơ}…). Không dùng
     * {@code Character.UnicodeBlock} vì {@code ă ê ô ơ ư đ} nằm ở 3 block
     * khác nhau – kiểm theo block sẽ bỏ sót và dương tính giả.
     */
    private static boolean hasDiacritics(
            String text
    ) {
        return text.codePoints()
                .anyMatch(codePoint ->
                        Character.isLetter(codePoint)
                                && codePoint > 127
                );
    }

    private static boolean isSymbols(
            String text
    ) {
        return text.codePoints()
                .noneMatch(Character::isLetterOrDigit);
    }

    private static boolean hasTypo(
            String text
    ) {

        String lower =
                text.toLowerCase(Locale.ROOT);

        return TYPO_TOKENS
                .stream()
                .anyMatch(lower::contains);
    }

    private static int words(
            String text
    ) {
        return text.trim()
                .split("\\s+").length;
    }

    private static int count(
            String text,
            char character
    ) {

        int total =
                0;

        for (char current : text.toCharArray()) {
            if (current == character) {
                total++;
            }
        }

        return total;
    }

    // ------------------------------------------------------------------
    // Ghi file
    // ------------------------------------------------------------------

    private static String toJsonl(
            String text,
            String intent,
            String tier,
            List<String> tags,
            String role,
            String group
    ) {

        Map<String, Object> node =
                new LinkedHashMap<>();

        node.put("text", text);
        node.put("intent", intent);
        node.put("tier", tier);
        node.put("tags", tags);
        node.put("role", role);
        node.put("group", group);

        return writeJson(node);
    }

    private static String toJsonl(
            String originalLine,
            String reason
    ) {

        try {
            Map<String, Object> raw =
                    MAPPER.readValue(
                            originalLine,
                            Map.class
                    );

            Map<String, Object> node =
                    new LinkedHashMap<>();

            node.put("text", raw.get("text"));
            node.put("intent", raw.get("intent"));
            node.put("group", raw.get("group"));
            node.put("reason", reason);

            return writeJson(node);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không chuyển được dòng mơ hồ",
                    exception
            );
        }
    }

    private static String writeJson(
            Map<String, Object> node
    ) {

        try {
            return MAPPER.writeValueAsString(node);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không serialize được JSONL",
                    exception
            );
        }
    }

    private static void write(
            Path path,
            List<String> lines
    ) throws Exception {

        StringBuilder builder =
                new StringBuilder();

        lines.forEach(line ->
                builder.append(line)
                        .append('\n')
        );

        Files.writeString(
                path,
                builder.toString(),
                StandardCharsets.UTF_8
        );

        System.out.println(
                "Đã ghi " + path
                        + " (" + lines.size() + " dòng)"
        );
    }
}
