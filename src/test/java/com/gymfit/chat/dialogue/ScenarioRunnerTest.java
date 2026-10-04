package com.gymfit.chat.dialogue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.chat.dto.ChatCard;
import com.gymfit.chat.dto.ChatRequest;
import com.gymfit.chat.dto.ChatResponse;
import com.gymfit.chat.session.RateLimiter;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.util.TimeUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chạy kịch bản hội thoại JSON qua {@link DialogueManager} <b>thật</b>
 * (NLU thật, handler thật, service mock) — plan V2, mục 7.1.
 *
 * <p>Đọc toàn bộ {@code src/test/resources/scenarios/*.json}. Kịch bản của
 * intent chưa làm được đánh dấu {@code "status":"PENDING"} → runner bỏ qua
 * và in cảnh báo (không fail build).
 *
 * <p>Thời điểm chạy được ghim bằng {@code TimeUtil.useClock} theo trường
 * {@code now} của kịch bản → dữ liệu fixture (ngày hiệu lực gói, ca tập)
 * không phụ thuộc ngày máy chạy; đồng hồ được trả về trong {@code finally}.
 *
 * <p>Đặt {@code -Dscenario.dump=true} để in nội dung thật của từng lượt —
 * dùng khi viết/sửa kỳ vọng {@code contains}.
 */
class ScenarioRunnerTest {

    private static final Path SCENARIO_DIR =
            Path.of("src", "test", "resources", "scenarios");

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final String SESSION_ID =
            "s1";

    /**
     * File dump UTF-8 của {@code -Dscenario.dump=true} — console Maven trên
     * Windows hay bị mojibake nên nội dung thật của từng lượt phải ghi ra
     * file để đọc/sửa kỳ vọng {@code contains}.
     */
    private static final Path DUMP_FILE =
            Path.of("target", "scenario-dump.txt");

    private static final Instant DEFAULT_NOW =
            ScenarioFixtures.DEFAULT_NOW;

    // ------------------------------------------------------------------
    // Nạp kịch bản
    // ------------------------------------------------------------------

    static Stream<Scenario> scenarios() throws Exception {

        assertTrue(
                Files.isDirectory(SCENARIO_DIR),
                "Thiếu thư mục kịch bản: " + SCENARIO_DIR
        );

        // Truncate file dump của lần chạy trước (scenarios() chạy 1 lần /
        // JVM test)
        if (Boolean.getBoolean("scenario.dump")) {

            Files.createDirectories(
                    DUMP_FILE.getParent()
            );

            Files.write(
                    DUMP_FILE,
                    new byte[0]
            );
        }

        List<Scenario> scenarios =
                new ArrayList<>();

        try (var files = Files.list(SCENARIO_DIR)) {

            files.filter(path ->
                            path.getFileName()
                                    .toString()
                                    .endsWith(".json")
            )
                    .sorted()
                    .forEach(file -> {

                        try {
                            parseFile(
                                    file,
                                    scenarios
                            );

                        } catch (Exception exception) {
                            throw new IllegalStateException(
                                    "Không đọc được " + file,
                                    exception
                            );
                        }
                    });
        }

        assertTrue(
                !scenarios.isEmpty(),
                "Không có kịch bản nào trong " + SCENARIO_DIR
        );

        return scenarios.stream();
    }

    private static void parseFile(
            Path file,
            List<Scenario> target
    ) throws Exception {

        JsonNode root =
                MAPPER.readTree(
                        Files.readString(
                                file,
                                StandardCharsets.UTF_8
                        )
                );

        assertTrue(
                root.isArray(),
                file + " phải là mảng JSON"
        );

        for (JsonNode node : root) {

            String id =
                    text(node, "id");

            assertNotNull(
                    id,
                    "Kịch bản thiếu id trong " + file
            );

            List<Turn> turns =
                    new ArrayList<>();

            JsonNode turnNodes =
                    node.path("turns");

            for (JsonNode turnNode : turnNodes) {

                turns.add(
                        new Turn(
                                text(turnNode, "say"),
                                text(turnNode, "payload"),
                                expectation(turnNode.path("expect")),
                                text(turnNode, "status"),
                                turnNode.path("advanceMin")
                                        .isInt()
                                        ? turnNode.path("advanceMin")
                                        .asInt()
                                        : null
                        )
                );
            }

            target.add(
                    new Scenario(
                            id,
                            text(node, "title"),
                            text(node, "role"),
                            text(node, "fixture"),
                            text(node, "now"),
                            text(node, "status"),
                            text(node, "note"),
                            turns,
                            tags(node),
                            file.getFileName()
                                    .toString()
                    )
            );
        }
    }

    private static Expectation expectation(
            JsonNode node
    ) {

        if (node == null || node.isMissingNode()) {
            return null;
        }

        return new Expectation(
                text(node, "intent"),
                strings(node, "contains"),
                strings(node, "notContains"),
                node.path("suggestions_min")
                        .isInt()
                        ? node.path("suggestions_min")
                        .asInt()
                        : null,
                strings(node, "services_called"),
                strings(node, "services_never"),
                node.path("card")
                        .isBoolean()
                        ? node.path("card")
                        .asBoolean()
                        : null,
                node.path("confidence_min")
                        .isNumber()
                        ? node.path("confidence_min")
                        .asDouble()
                        : null,
                node.path("card_lines_min")
                        .isInt()
                        ? node.path("card_lines_min")
                        .asInt()
                        : null
        );
    }

    private static String text(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        return value.isTextual()
                ? value.asText()
                : null;
    }

    private static List<String> strings(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.path(field);

        List<String> result =
                new ArrayList<>();

        if (value.isArray()) {
            value.forEach(item ->
                    result.add(
                            item.asText()
                    )
            );
        }

        return result;
    }

    private static List<String> tags(
            JsonNode node
    ) {
        return strings(node, "tags");
    }

    // ------------------------------------------------------------------
    // Chạy
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("Kịch bản hội thoại")
    void runScenario(
            Scenario scenario
    ) throws Exception {

        if ("PENDING".equals(scenario.status())) {

            String reason =
                    scenario.note() == null
                            ? "intent chưa làm"
                            : scenario.note();

            System.out.println(
                    "[PENDING] " + scenario.id()
                            + " — " + reason
            );

            Assumptions.assumeTrue(
                    false,
                    "PENDING — " + reason
            );
        }

        Instant now =
                scenario.now() == null
                        ? DEFAULT_NOW
                        : Instant.parse(
                        scenario.now()
                );

        TimeUtil.useClock(
                Clock.fixed(
                        now,
                        TimeUtil.VIETNAM
                )
        );

        try {

            ScenarioFixtures.Harness harness =
                    new ScenarioFixtures.Harness();

            AppPrincipal principal =
                    ScenarioFixtures.apply(
                            harness,
                            scenario.fixture()
                    );

            assertEquals(
                    expectedRole(
                            scenario.role()
                    ),
                    principal.getRole()
                            .name(),
                    scenario.id()
                            + ": role trong JSON phải khớp fixture "
                            + scenario.fixture()
            );

            Integer rateLimit =
                    scenario.rateLimit();

            if (rateLimit != null) {

                RateLimiter limiter =
                        (RateLimiter) ReflectionTestUtils.getField(
                                harness.manager,
                                "rateLimiter"
                        );

                assertNotNull(limiter);

                limiter.setLimit(
                        rateLimit
                );
            }

            ChatCard lastCard =
                    null;

            for (int index = 0;
                 index < scenario.turns().size();
                 index++) {

                Turn turn =
                        scenario.turns()
                                .get(index);

                harness.beginTurn();

                if (turn.advanceMin() != null
                        && turn.advanceMin() > 0) {

                    // Ghim đồng hồ tiến thêm N phút — dùng cho ca
                    // "bấm Xác nhận sau khi pending hết hạn".
                    TimeUtil.useClock(
                            Clock.fixed(
                                    now.plusSeconds(
                                            turn.advanceMin() * 60L
                                    ),
                                    TimeUtil.VIETNAM
                            )
                    );
                }

                String payload =
                        payload(
                                turn,
                                lastCard
                        );

                ChatRequest request =
                        payload != null
                                ? new ChatRequest(
                                null,
                                SESSION_ID,
                                payload
                        )
                                : new ChatRequest(
                                turn.say(),
                                SESSION_ID,
                                null
                        );

                ChatResponse response =
                        harness.manager.handle(
                                principal,
                                request
                        );

                if (response.card() != null) {
                    lastCard = response.card();
                }

                if (Boolean.getBoolean("scenario.dump")) {

                    dump(
                            "[DUMP] " + scenario.id()
                                    + " T" + (index + 1)
                                    + " say=" + turn.say()
                                    + " | payload=" + payload
                                    + " | intent=" + response.intent()
                                    + " | msg=" + response.message()
                                    + " | card=" + (response.card() == null
                                    ? "null"
                                    : response.card().type()
                                    + "/" + response.card().title())
                    );
                }

                assertTurn(
                        scenario,
                        index,
                        turn,
                        response,
                        harness.calls(),
                        payload
                );
            }

        } finally {
            TimeUtil.resetClock();
        }
    }

    /**
     * Biến token đặc biệt thành payload thật lấy từ thẻ của lượt trước —
     * đúng cách client thật bấm nút ({@code CONFIRM:<uuid>} do server sinh).
     *
     * <p>{@code $CONFIRM} → {@code card.confirmPayload},
     * {@code $CANCEL} → {@code card.cancelPayload}.
     */
    private static String payload(
            Turn turn,
            com.gymfit.chat.dto.ChatCard card
    ) {

        String raw =
                turn.payload();

        if (raw == null) {
            return null;
        }

        if ("$CONFIRM".equals(raw)) {

            assertTrue(
                    card != null
                            && card.confirmPayload() != null,
                    "Lượt trước không trả thẻ Xác nhận"
            );

            return card.confirmPayload();
        }

        if ("$CANCEL".equals(raw)) {

            assertTrue(
                    card != null
                            && card.cancelPayload() != null,
                    "Lượt trước không trả thẻ Hủy"
            );

            return card.cancelPayload();
        }

        return raw;
    }

    private static void assertTurn(
            Scenario scenario,
            int index,
            Turn turn,
            ChatResponse response,
            List<String> calls,
            String payload
    ) {

        String where =
                scenario.id()
                        + " lượt " + (index + 1)
                        + " (say=\"" + turn.say()
                        + "\", payload=" + payload + ")\n"
                        + "  → intent=" + response.intent()
                        + "\n  → msg=" + response.message();

        assertNotNull(
                response,
                where
        );

        Expectation expected =
                turn.expect();

        if (expected == null) {
            return;
        }

        String message =
                response.message() == null
                        ? ""
                        : response.message();

        if (expected.intent() != null) {

            assertEquals(
                    expected.intent(),
                    response.intent(),
                    "Sai intent ở " + where
            );
        }

        for (String needle : expected.contains()) {

            assertTrue(
                    containsIgnoreCase(message, needle),
                    "Thiếu \"" + needle + "\" ở " + where
            );
        }

        for (String needle : expected.notContains()) {

            assertTrue(
                    !containsIgnoreCase(message, needle),
                    "Không được chứa \"" + needle + "\" ở " + where
            );
        }

        if (expected.suggestionsMin() != null) {

            int size =
                    response.suggestions() == null
                            ? 0
                            : response.suggestions().size();

            assertTrue(
                    size >= expected.suggestionsMin(),
                    "Cần ≥ " + expected.suggestionsMin()
                            + " gợi ý, hiện " + size
                            + " ở " + where
            );
        }

        if (expected.card() != null) {

            assertEquals(
                    expected.card(),
                    response.card() != null,
                    "Thẻ xác nhận không đúng ở " + where
            );
        }

        if (expected.cardLinesMin() != null) {

            assertTrue(
                    response.card() != null,
                    "Cần thẻ có ≥ " + expected.cardLinesMin()
                            + " dòng ở " + where
            );

            int lines =
                    response.card().lines() == null
                            ? 0
                            : response.card().lines().size();

            assertTrue(
                    lines >= expected.cardLinesMin(),
                    "Thẻ cần ≥ " + expected.cardLinesMin()
                            + " dòng, hiện " + lines
                            + " ở " + where
            );
        }

        if (expected.confidenceMin() != null) {

            double confidence =
                    response.confidence() == null
                            ? 0
                            : response.confidence();

            assertTrue(
                    confidence >= expected.confidenceMin(),
                    "confidence " + confidence
                            + " < " + expected.confidenceMin()
                            + " ở " + where
            );
        }

        for (String service : expected.servicesCalled()) {

            assertTrue(
                    calls.contains(service),
                    "Phải gọi " + service
                            + " ở " + where
                            + "\n  đã gọi: " + calls
            );
        }

        for (String service : expected.servicesNever()) {

            assertTrue(
                    !calls.contains(service),
                    "Không được gọi " + service
                            + " ở " + where
                            + "\n  đã gọi: " + calls
            );
        }
    }

    /** In 1 dòng dump ra console <b>và</b> ghi vào file UTF-8. */
    private static void dump(
            String line
    ) {

        System.out.println(line);

        try {

            Files.write(
                    DUMP_FILE,
                    (line + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );

        } catch (Exception exception) {

            System.out.println(
                    "Không ghi được dump: " + exception.getMessage()
            );
        }
    }

    /**
     * {@code MEMBER}/{@code STAFF}/{@code ADMIN} trong JSON là vai trò giao
     * diện (giống holdout); {@code STAFF} tương ứng {@code BRANCH_MANAGER}
     * trong {@code RoleCode} — không có vai trò ADMIN/STAFF chung chung.
     */
    private static String expectedRole(
            String role
    ) {
        if ("STAFF".equals(role)) {
            return "BRANCH_MANAGER";
        }

        return role;
    }

    private static boolean containsIgnoreCase(
            String haystack,
            String needle
    ) {
        return haystack
                .toLowerCase(Locale.ROOT)
                .contains(
                        needle.toLowerCase(Locale.ROOT)
                );
    }

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    /**
     * @param id        mã kịch bản (BK-01…)
     * @param title     tiêu đề
     * @param role      vai trò kỳ vọng ({@code MEMBER}/{@code STAFF}/{@code ADMIN})
     * @param fixture   tên fixture trong {@link ScenarioFixtures}
     * @param now       thời điểm ghim (ISO-8601); null = mặc định
     * @param status    {@code PENDING} → bỏ qua có cảnh báo
     * @param note      lý do PENDING
     * @param turns     các lượt hội thoại
     * @param tags      nhãn phân loại
     * @param file      tên file chứa kịch bản
     */
    record Scenario(
            String id,
            String title,
            String role,
            String fixture,
            String now,
            String status,
            String note,
            List<Turn> turns,
            List<String> tags,
            String file
    ) {

        Integer rateLimit() {

            for (String tag : tags) {
                if (tag.startsWith("rate-limit=")) {
                    return Integer.parseInt(
                            tag.substring(
                                    "rate-limit=".length()
                            )
                    );
                }
            }

            return null;
        }

        @Override
        public String toString() {
            return id + " — " + title;
        }
    }

    record Turn(
            String say,
            String payload,
            Expectation expect,
            String status,
            Integer advanceMin
    ) {
    }

    record Expectation(
            String intent,
            List<String> contains,
            List<String> notContains,
            Integer suggestionsMin,
            List<String> servicesCalled,
            List<String> servicesNever,
            Boolean card,
            Double confidenceMin,
            Integer cardLinesMin
    ) {
    }
}
