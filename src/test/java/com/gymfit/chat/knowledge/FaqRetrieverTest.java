package com.gymfit.chat.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.user.RoleCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-3 — kiểm chứng {@link FaqRetriever} (plan 5.3 + AC của task).
 *
 * <p>Bộ truy vấn nằm ở {@code src/test/resources/faq_queries.jsonl}:
 * 300 câu có nhãn ({@code q → entryId}) để đo recall@1/@3, và 50 câu ngoài kho
 * ({@code entryId: null}) bắt buộc phải miss.
 */
class FaqRetrieverTest {

    /** Cổng tối thiểu của plan: recall@1 ≥ 0,85, recall@3 ≥ 0,95. */
    private static final double GATE_RECALL_AT_1 =
            0.85;

    private static final double GATE_RECALL_AT_3 =
            0.95;

    private static final int MIN_ENTRIES =
            70;

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static FaqKnowledgeBase knowledgeBase;

    private static FaqRetriever retriever;

    private record Row(
            String query,
            String entryId
    ) {
    }

    @BeforeAll
    static void setUp() {

        knowledgeBase =
                new FaqKnowledgeBase();

        knowledgeBase.load();

        retriever =
                new FaqRetriever(knowledgeBase);

        retriever.load();
    }

    // ------------------------------------------------------------------
    // Cấu trúc kho
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Kho FAQ ≥ 70 mục; mỗi mục có source + ≥ 6 câu hỏi + id duy nhất")
    void kbStructure() {

        List<FaqEntry> entries =
                knowledgeBase.entries();

        assertTrue(
                entries.size() >= MIN_ENTRIES,
                "Cần ≥ " + MIN_ENTRIES + " mục, thực tế " + entries.size()
        );

        Set<String> ids =
                new HashSet<>();

        for (FaqEntry entry : entries) {

            assertTrue(
                    ids.add(entry.id()),
                    "id trùng: " + entry.id()
            );

            assertTrue(
                    entry.source() != null
                            && !entry.source().isBlank(),
                    "thiếu source: " + entry.id()
            );

            assertTrue(
                    entry.questions().size()
                            >= FaqKnowledgeBase.MIN_QUESTIONS,
                    "id " + entry.id() + " chỉ có "
                            + entry.questions().size() + " câu hỏi"
            );

            assertTrue(
                    entry.answer() != null
                            && !entry.answer().isBlank(),
                    "thiếu answer: " + entry.id()
            );

            assertTrue(
                    entry.roles() != null
                            && !entry.roles().isEmpty(),
                    "thiếu roles: " + entry.id()
            );

            assertTrue(
                    FaqEntry.STATUS_ACTIVE.equals(entry.status())
                            || FaqEntry.STATUS_NOT_SUPPORTED.equals(entry.status())
                            || FaqEntry.STATUS_PENDING_FEATURE.equals(entry.status()),
                    "status lạ: " + entry.status()
            );
        }

        assertTrue(
                entries.stream()
                        .anyMatch(
                                entry -> FaqEntry.STATUS_PENDING_FEATURE
                                        .equals(entry.status())
                        ),
                "cần ít nhất 1 mục PENDING_FEATURE để kiểm điều kiện bỏ qua"
        );
    }

    @Test
    @DisplayName("Không có mục PENDING_FEATURE nào trong chỉ mục hay kết quả")
    void pendingFeatureNeverReturned() {

        List<String> pending =
                knowledgeBase.entries()
                        .stream()
                        .filter(
                                entry -> FaqEntry.STATUS_PENDING_FEATURE
                                        .equals(entry.status())
                        )
                        .map(FaqEntry::id)
                        .toList();

        assertFalse(
                pending.isEmpty(),
                "không có mục PENDING_FEATURE để kiểm"
        );

        for (FaqEntry entry : knowledgeBase.entries()) {

            if (!FaqEntry.STATUS_PENDING_FEATURE.equals(entry.status())) {
                continue;
            }

            // Truy vấn đúng từng câu của mục này vẫn không được trả về.
            for (String question : entry.questions()) {

                FaqRetriever.Result result =
                        retriever.retrieve(
                                question,
                                RoleCode.ADMIN
                        );

                assertTrue(
                        result.entry() == null
                                || !entry.id()
                                .equals(result.entry().id()),
                        "PENDING_FEATURE lọt vào kết quả: " + entry.id()
                );

                List<FaqEntry> top =
                        retriever.top(
                                question,
                                RoleCode.ADMIN,
                                knowledgeBase.size()
                        );

                assertTrue(
                        top.stream().noneMatch(
                                found -> entry.id()
                                        .equals(found.id())
                        ),
                        "PENDING_FEATURE lọt vào top-N: " + entry.id()
                );
            }
        }
    }

    // ------------------------------------------------------------------
    // Recall trên faq_queries.jsonl
    // ------------------------------------------------------------------

    @Test
    @DisplayName("300 truy vấn: recall@1 ≥ 0,85 và recall@3 ≥ 0,95")
    void recallAt1And3() throws Exception {

        List<Row> labelled =
                rows().stream()
                        .filter(row -> row.entryId() != null)
                        .toList();

        assertEquals(
                300,
                labelled.size(),
                "Cần đúng 300 truy vấn có nhãn"
        );

        int recall1 =
                0;

        int recall3 =
                0;

        List<String> misses =
                new ArrayList<>();

        for (Row row : labelled) {

            assertTrue(
                    knowledgeBase.byId(row.entryId()) != null,
                    "entryId không có trong kho: " + row.entryId()
            );

            List<FaqEntry> top =
                    retriever.top(
                            row.query(),
                            RoleCode.ADMIN,
                            3
                    );

            if (!top.isEmpty()
                    && row.entryId().equals(top.get(0).id())) {
                recall1++;
            }

            if (top.stream().anyMatch(
                    entry -> row.entryId().equals(entry.id())
            )) {
                recall3++;
            } else {

                FaqRetriever.Result head =
                        retriever.retrieve(
                                row.query(),
                                RoleCode.ADMIN
                        );

                misses.add(
                        row.query()
                                + "  [mong muốn=" + row.entryId() + "]"
                                + "  [top1=" + (head.entry() == null
                                ? "-"
                                : head.entry().id())
                                + " " + String.format("%.3f", head.score()) + "]"
                                + "  [top3=" + ids(top) + "]"
                );
            }
        }

        double at1 =
                recall1 / (double) labelled.size();

        double at3 =
                recall3 / (double) labelled.size();

        // Thống kê lỗi nhóm theo MỤC mục tiêu (để biết mục nào cần làm giàu).
        Map<String, Integer> rank1MissPerEntry =
                new java.util.TreeMap<>();

        Map<String, Integer> top3MissPerEntry =
                new java.util.TreeMap<>();

        for (Row row : labelled) {

            List<FaqEntry> top =
                    retriever.top(
                            row.query(),
                            RoleCode.ADMIN,
                            3
                    );

            if (top.isEmpty()
                    || !row.entryId().equals(top.get(0).id())) {

                rank1MissPerEntry.merge(
                        row.entryId(),
                        1,
                        Integer::sum
                );
            }

            if (top.stream().noneMatch(
                    entry -> row.entryId().equals(entry.id())
            )) {

                top3MissPerEntry.merge(
                        row.entryId(),
                        1,
                        Integer::sum
                );
            }
        }

        if (!rank1MissPerEntry.isEmpty()) {

            System.out.println("Sai hạng-1 theo mục mục tiêu:");

            rank1MissPerEntry.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry ->
                            System.out.printf(
                                    "  %-32s %d (lọt top-3: %d)%n",
                                    entry.getKey(),
                                    entry.getValue(),
                                    top3MissPerEntry.getOrDefault(entry.getKey(), 0)
                            )
                    );
        }

        System.out.printf(
                "FAQ recall@1 = %.4f (%d/%d), recall@3 = %.4f (%d/%d)",
                at1,
                recall1,
                labelled.size(),
                at3,
                recall3,
                labelled.size()
        );

        System.out.println();

        if (!misses.isEmpty()) {

            System.out.println(
                    "Truy vấn miss top-3 (" + misses.size() + "):"
            );

            misses.forEach(miss -> System.out.println("  - " + miss));
        }

        assertTrue(
                at1 >= GATE_RECALL_AT_1,
                String.format(
                        "recall@1 = %.4f < %.2f",
                        at1,
                        GATE_RECALL_AT_1
                )
        );

        assertTrue(
                at3 >= GATE_RECALL_AT_3,
                String.format(
                        "recall@3 = %.4f < %.2f",
                        at3,
                        GATE_RECALL_AT_3
                )
        );
    }

    @Test
    @DisplayName("50 truy vấn ngoài kho phải miss (điểm < ngưỡng suggest)")
    void outOfScopeMisses() throws Exception {

        List<Row> outOfScope =
                rows().stream()
                        .filter(row -> row.entryId() == null)
                        .toList();

        assertEquals(
                50,
                outOfScope.size(),
                "Cần đúng 50 truy vấn ngoài kho"
        );

        List<String> wrong =
                new ArrayList<>();

        for (Row row : outOfScope) {

            FaqRetriever.Result result =
                    retriever.retrieve(
                            row.query(),
                            RoleCode.ADMIN
                    );

            if (result.status() != FaqRetriever.Status.MISS
                    || result.score()
                    >= FaqRetriever.THRESHOLD_SUGGEST) {

                wrong.add(
                        row.query() + " -> "
                                + result.status() + " ("
                                + String.format("%.3f", result.score()) + ")"
                );
            }
        }

        if (!wrong.isEmpty()) {

            System.out.println(
                    "Truy vấn ngoài kho bị trả lời sai (" + wrong.size() + "):"
            );

            wrong.forEach(line -> System.out.println("  - " + line));
        }

        assertTrue(
                wrong.isEmpty(),
                "Có " + wrong.size() + " truy vấn ngoài kho bị match: " + wrong
        );
    }

    // ------------------------------------------------------------------
    // Lọc vai trò / trạng thái
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mục ADMIN-only không xuất hiện với vai trò MEMBER")
    void roleFiltering() {

        String question =
                "ai vừa thao tác dữ liệu này";

        List<FaqEntry> asAdmin =
                retriever.top(
                        question,
                        RoleCode.ADMIN,
                        5
                );

        assertTrue(
                asAdmin.stream().anyMatch(
                        entry -> "admin_audit".equals(entry.id())
                ),
                "ADMIN phải thấy mục admin_audit, got: " + ids(asAdmin)
        );

        List<FaqEntry> asMember =
                retriever.top(
                        question,
                        RoleCode.MEMBER,
                        knowledgeBase.size()
                );

        assertFalse(
                asMember.stream().anyMatch(
                        entry -> entry.id().startsWith("admin_")
                ),
                "MEMBER không được thấy mục admin-only: " + ids(asMember)
        );
    }

    @Test
    @DisplayName("Mục NOT_SUPPORTED vẫn trúng và giữ nguyên trạng thái")
    void notSupportedEntriesAreRetrievable() {

        FaqRetriever.Result result =
                retriever.retrieve(
                        "tôi muốn lấy lại tiền gói",
                        RoleCode.MEMBER
                );

        assertTrue(
                result.hit(),
                "Câu hỏi hoàn tiền phải trúng kho, điểm = "
                        + String.format("%.3f", result.score())
        );

        assertEquals(
                "payment_refund",
                result.entry().id()
        );

        assertEquals(
                FaqEntry.STATUS_NOT_SUPPORTED,
                result.entry().status()
        );
    }

    @Test
    @DisplayName("Câu trả lời HIT luôn kèm card và nội dung")
    void hitCarriesAnswerAndCard() {

        FaqRetriever.Result result =
                retriever.retrieve(
                        "gói tập có những cấp nào",
                        RoleCode.MEMBER
                );

        assertTrue(result.hit());

        assertNotNull(result.entry());

        assertFalse(
                result.entry().answer().isBlank()
        );

        assertFalse(
                result.entry().link().isBlank()
        );
    }

    @Test
    @DisplayName("Truy vấn rỗng → miss, không ném lỗi")
    void emptyQueryIsMiss() {

        for (String query : new String[]{null, "", "   ", "..."}) {

            FaqRetriever.Result result =
                    retriever.retrieve(
                            query,
                            RoleCode.MEMBER
                    );

            assertEquals(
                    FaqRetriever.Status.MISS,
                    result.status(),
                    "query = [" + query + "]"
            );
        }
    }

    @Test
    @DisplayName("Chỉ mục dựng đúng số vector = tổng số câu hỏi của mục KHÔNG pending")
    void indexSizeMatchesQuestions() {

        int expected =
                knowledgeBase.entries()
                        .stream()
                        .filter(FaqEntry::retrievable)
                        .mapToInt(entry -> entry.questions().size())
                        .sum();

        assertEquals(
                expected,
                retriever.indexSize()
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<Row> rows() throws Exception {

        try (InputStream input =
                     FaqRetrieverTest.class.getResourceAsStream(
                             "/faq_queries.jsonl"
                     )) {

            assertNotNull(
                    input,
                    "Không tìm thấy /faq_queries.jsonl"
            );

            List<Row> rows =
                    new ArrayList<>();

            for (String line : new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            ).split("\n")) {

                if (line.isBlank()) {
                    continue;
                }

                JsonNode node =
                        MAPPER.readTree(line);

                String entryId =
                        node.path("entryId").isNull()
                                || !node.has("entryId")
                                ? null
                                : node.path("entryId").asText();

                rows.add(
                        new Row(
                                node.path("q").asText(),
                                entryId == null || entryId.isBlank()
                                        ? null
                                        : entryId
                        )
                );
            }

            return rows;
        }
    }

    private static List<String> ids(
            List<FaqEntry> entries
    ) {
        return entries.stream()
                .map(FaqEntry::id)
                .toList();
    }
}
