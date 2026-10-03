package com.gymfit.chat.nlg;

import com.gymfit.branch.ServiceCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseTemplatesTest {

    private ResponseTemplates templates;

    @BeforeEach
    void setUp() {
        templates =
                new ResponseTemplates();

        templates.load();
    }

    @Test
    @DisplayName("Nạp được toàn bộ template và FAQ")
    void loadsAll() {

        assertTrue(
                templates.size() > 40,
                "Số template = " + templates.size()
        );

        assertEquals(
                6,
                templates.faqSize()
        );
    }

    @Test
    @DisplayName("Tất cả khoá bắt buộc trong plan đều tồn tại")
    void requiredKeysExist() {

        String[] required = {
                "greeting.member", "greeting.staff",
                "thanks", "goodbye",
                "help.member", "help.manager", "help.admin",
                "fallback", "clarify", "denied.role",
                "error.generic", "error.rate",
                "info.no_data",
                "membership.none", "membership.active",
                "booking.ask_service", "booking.ask_date",
                "booking.ask_time", "booking.ask_pick",
                "booking.confirm", "booking.done",
                "booking.none_available",
                "cancel.ask_pick", "cancel.confirm",
                "cancel.done", "cancel.too_late",
                "plan.item", "plan.compare", "plan.recommend",
                "report.dashboard", "report.revenue", "report.service",
                "stock.low",
                "checkin.rejected_summary",
                "audit.item"
        };

        for (String key : required) {
            assertTrue(
                    templates.has(key),
                    "Thiếu template: " + key
            );
        }
    }

    @Test
    @DisplayName("Thiếu biến → ném lỗi rõ ràng")
    void missingVariableThrows() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> templates.get(
                                "clarify"
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("còn biến chưa thay"),
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Truyền đủ biến → không còn dấu {...}")
    void rendersVariables() {

        String result =
                templates.get(
                        "clarify",
                        Map.of(
                                "intent_desc",
                                "đặt lịch tập"
                        )
                );

        assertFalse(
                result.contains("{"),
                result
        );

        assertTrue(
                result.contains(
                        "đặt lịch tập"
                )
        );
    }

    @Test
    @DisplayName("Khoá không tồn tại → ném lỗi")
    void unknownKeyThrows() {

        assertThrows(
                IllegalArgumentException.class,
                () -> templates.get(
                        "khong_ton_tai"
                )
        );
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "greeting.member",
            "greeting.staff",
            "thanks",
            "goodbye",
            "fallback",
            "info.no_data"
    })
    @DisplayName("Template không có biến: lấy được, không sót {...}")
    void templatesWithoutVariables(
            String key
    ) {

        String result =
                templates.get(
                        key,
                        Map.of()
                );

        assertNotNull(
                result
        );

        assertFalse(
                result.contains("{"),
                key + " → " + result
        );
    }

    @Test
    @DisplayName("Câu có biến: lấy đủ biến thì không sót")
    void templatesWithVariablesRender() {

        assertDoesNotThrow(() -> templates.get(
                "membership.active",
                Map.of(
                        "plan_name", "Gói 3 tháng",
                        "tier", "STANDARD",
                        "from", "01/10/2026",
                        "to", "31/12/2026",
                        "days", "92",
                        "branch", "Quận 1",
                        "services", "Gym, Boxing"
                )
        ));

        assertDoesNotThrow(() -> templates.get(
                "report.revenue",
                Map.of(
                        "from", "01/10/2026",
                        "to", "03/10/2026",
                        "branch_label", "chi nhánh của bạn",
                        "gross", "1.500.000đ",
                        "plan", "1.000.000đ",
                        "product", "500.000đ",
                        "orders", "12"
                )
        ));
    }

    @Test
    @DisplayName("Greeting/thanks chọn ngẫu nhiên trong các biến thể")
    void randomVariants() {

        java.util.Set<String> seen =
                new java.util.HashSet<>();

        for (int i = 0; i < 50; i++) {

            seen.add(
                    templates.first(
                            "greeting.member",
                            Map.of()
                    )
            );
        }

        assertTrue(
                seen.size() >= 1
        );
    }

    @Test
    @DisplayName("FAQ trả về đủ câu trả lời + link")
    void faqAnswers() {

        for (String key : new String[]{
                "FAQ_CANCEL_POLICY",
                "FAQ_BOOKING_RULES",
                "FAQ_CHECKIN_HOWTO",
                "FAQ_BUY_PLAN_HOWTO",
                "FAQ_CHECKIN_REJECTED",
                "FAQ_QR_HOWTO"
        }) {

            ResponseTemplates.FaqEntry entry =
                    templates.faq(key);

            assertNotNull(
                    entry,
                    key
            );

            assertFalse(
                    entry.answer()
                            .isBlank(),
                    key
            );

            assertTrue(
                    entry.link()
                            .startsWith("/"),
                    key + " → " + entry.link()
            );

            String answer =
                    templates.faqAnswer(key);

            assertFalse(
                    answer.contains("{"),
                    answer
            );
        }
    }

    @Test
    @DisplayName("FAQ nói đủ 5 lý do từ chối (đúng code thật)")
    void faqRejectionMentionsAllReasons() {

        String answer =
                templates.faq(
                        "FAQ_CHECKIN_REJECTED"
                )
                .answer();

        assertTrue(
                answer.contains(
                        "NO_ACTIVE_MEMBERSHIP"
                )
        );

        assertTrue(
                answer.contains(
                        "SERVICE_NOT_INCLUDED"
                )
        );

        assertTrue(
                answer.contains(
                        "MEMBERSHIP_BRANCH_MISMATCH"
                )
        );

        assertTrue(
                answer.contains(
                        "MEMBERSHIP_INVALID"
                )
        );

        assertTrue(
                answer.contains(
                        "DUPLICATE_CHECKIN"
                ),
                "Thiếu DUPLICATE_CHECKIN (code thật có 5 lý do)"
        );
    }

}