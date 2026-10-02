package com.gymfit.chat.nlu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNormalizerTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer =
                new TextNormalizer();

        normalizer.load();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource(
            delimiter = '|',
            value = {
                    "Đặt lịch ngày mai ạ|dat lich ngay mai",
                    "ko đc hủy ak|khong duoc huy",
                    "Gym Q7 bn tiền?|gym q7 bao nhieu tien?",
                    "  GÓI   PREMIUM |goi premium",
                    "hnay mấy giờ mở cửa|hom nay may gio mo cua",
                    "Tôi muốn đặt Boxing ở Q1|toi muon dat boxing o q1",
                    "cho mình xem lịch sắp tới|cho minh xem lich sap toi",
                    "Pickleball sân ngoài trời|pickleball san ngoai troi",
                    "j có lịch tập j|gi co lich tap gi",
                    "pls đặt lịch giúp mình|vui long dat lich giup minh",
                    "hủy lịch 5/10 7h tối|huy lich 5/10 7h toi",
                    "gói premium dưới 600k|goi premium duoi 600k",
                    "sáng mai 7h chiều tối|sang mai 7h chieu toi",
                    "hỏi về thẻ thành viên|hoi ve the thanh vien",
                    "m vs b|m voi b",
                    "BOOK_0123456789ABCDEF|book_0123456789abcdef",
                    "đ đ ư ợ|d d u o",
                    "so diện thoại 0912345678|so dien thoai 0912345678",
                    "báo cáo doanh thu tháng này|bao cao doanh thu thang nay",
                    "check-in bị từ chối vì sao|check-in bi tu choi vi sao"
            }
    )
    @DisplayName("Chuẩn hóa văn bản tiếng Việt + teencode")
    void normalize_shouldProducePlainText(
            String raw,
            String expected
    ) {
        assertEquals(
                expected,
                normalizer.normalize(raw)
                        .plain()
        );
    }

    @Test
    @DisplayName("Giữ nguyên raw để lưu log")
    void normalize_shouldKeepRaw() {
        NormalizedText result =
                normalizer.normalize("  Đặt lịch ngày mai ạ  ");

        assertEquals(
                "Đặt lịch ngày mai ạ",
                result.raw()
        );

        assertEquals(
                "dat lich ngay mai",
                result.plain()
        );
    }

    @Test
    @DisplayName("Chuẩn hóa là idempotent")
    void normalize_shouldBeIdempotent() {
        String[] samples = {
                "Đặt lịch ngày mai ạ",
                "ko đc hủy ak",
                "Gym Q7 bn tiền?",
                "hnay mấy giờ mở cửa",
                "  GÓI   PREMIUM ",
                "cho mình xem lịch sắp tới"
        };

        for (String sample : samples) {

            String once =
                    normalizer.normalize(sample)
                            .plain();

            String twice =
                    normalizer.normalize(once)
                            .plain();

            assertEquals(
                    once,
                    twice,
                    "Không idempotent với: " + sample
            );
        }
    }

    @Test
    @DisplayName("Rút gọn ký tự lặp quá 2 lần")
    void normalize_shouldCollapseRepeatedCharacters() {
        assertEquals(
                "duocc",
                normalizer.normalize("đượcccc")
                        .plain()
        );

        assertEquals(
                "kk",
                normalizer.normalize("kkkk")
                        .plain()
        );
    }

    @Test
    @DisplayName("Chuỗi rỗng, null, emoji, ký tự lạ — không ném lỗi")
    void normalize_shouldHandleEdgeCases() {
        assertEquals(
                "",
                normalizer.normalize(null)
                        .plain()
        );

        assertEquals(
                "",
                normalizer.normalize("")
                        .plain()
        );

        assertEquals(
                "",
                normalizer.normalize("     ")
                        .plain()
        );

        String emoji =
                normalizer.normalize("😀🎉🔥")
                        .plain();

        assertNotNull(emoji);
        assertTrue(
                emoji.isBlank(),
                "Emoji phải rỗng sau khi lọc, nhận được: " + emoji
        );

        assertDoesNotThrow(() ->
                normalizer.normalize("@@@###$$$%%%^^^&&&***(((")
        );
    }

    @Test
    @DisplayName("Giá trị trả về không null")
    void normalize_shouldNeverReturnNull() {
        assertNotNull(
                normalizer.normalize(null)
        );

        assertNotNull(
                normalizer.normalize(null)
                        .raw()
        );
    }

    @Test
    @DisplayName("Bảng teencode được nạp đúng từ synonyms.json")
    void load_shouldReadTeencodeTable() {
        assertEquals(
                "khong",
                TextNormalizer.loadTeencode(
                        TextNormalizer.RESOURCE
                ).get("ko")
        );

        assertEquals(
                "",
                TextNormalizer.loadTeencode(
                        TextNormalizer.RESOURCE
                ).get("a")
        );
    }

    @Test
    @DisplayName("toPlain() bỏ dấu độc lập pipeline")
    void toPlain_shouldStripDiacritics() {
        assertEquals(
                "thu duc",
                TextNormalizer.toPlain("Thủ Đức")
        );

        assertEquals(
                "",
                TextNormalizer.toPlain(null)
        );
    }

}