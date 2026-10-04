package com.gymfit.invoice;

import com.gymfit.invoice.dto.InvoiceData;
import com.gymfit.invoice.dto.InvoiceItemResponse;
import com.gymfit.order.OrderItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Luồng xuất hóa đơn PDF (yêu cầu: "xuất hóa đơn có tải về dạng pdf").
 *
 * <p>Chứng minh {@link InvoicePdfService} render ra <b>file PDF thật</b>
 * (không phải HTML gắn đuôi .pdf): đủ magic {@code %PDF-}, đủ {@code %%EOF},
 * đủ kích thước – nếu template hoặc font lỗi thì openhtmltopdf sẽ ném
 * {@code IllegalStateException} ngay tại đây.
 */
@SpringBootTest
class InvoicePdfServiceTest {

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Test
    @DisplayName("render trả về file PDF hợp lệ (%PDF-, %%EOF, đủ kích thước)")
    void renderPdfHopLe() {

        InvoiceData hoaDon = new InvoiceData(
                1L,
                "ORD_TEST_000001",
                "Chi nhanh Quan 1",
                "123 Nguyen Trai, Quan 1, TP.HCM",
                "02838001234",
                "Nguyen Van A",
                "GF00000001",
                "PAY_TEST_001",
                "MOMO",
                "REF_TEST_001",
                Instant.parse("2026-10-04T03:00:00Z"),
                new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                List.of(
                        new InvoiceItemResponse(
                                "Goi Gym 1 thang",
                                OrderItemType.PLAN,
                                new BigDecimal("1000000"),
                                1,
                                new BigDecimal("1000000")
                        )
                )
        );

        byte[] pdf = invoicePdfService.render(hoaDon);

        // magic của PDF
        String head = new String(
                pdf, 0, 5, StandardCharsets.US_ASCII
        );

        assertEquals("%PDF-", head, "Thieu magic %PDF-");

        // kết thúc có %%EOF
        int offset = Math.max(0, pdf.length - 64);

        String tail = new String(
                pdf, offset, pdf.length - offset, StandardCharsets.US_ASCII
        );

        assertTrue(
                tail.contains("%%EOF"),
                "Thieu %%EOF o cuoi file: " + tail
        );

        // quá nhỏ nghĩa là render rỗng / lỗi im lặng
        assertTrue(
                pdf.length > 2000,
                "PDF qua nho: " + pdf.length + " byte"
        );
    }

    @Test
    @DisplayName("render hóa đơn không có tham chiếu MoMo vẫn được (providerReference = null)")
    void renderKhiKhongCoProviderReference() {

        InvoiceData hoaDon = new InvoiceData(
                2L,
                "ORD_TEST_000002",
                "Chi nhanh Quan 1",
                "123 Nguyen Trai, Quan 1, TP.HCM",
                null,
                "Khach le",
                null,
                "PAY_TEST_002",
                "CASH",
                null,
                null,
                new BigDecimal("500000"),
                new BigDecimal("500000"),
                List.of()
        );

        byte[] pdf = invoicePdfService.render(hoaDon);

        String head = new String(
                pdf, 0, 5, StandardCharsets.US_ASCII
        );

        assertEquals("%PDF-", head);

        assertTrue(
                pdf.length > 2000,
                "PDF qua nho: " + pdf.length + " byte"
        );
    }
}
