package com.gymfit.invoice;

import com.gymfit.invoice.dto.InvoiceData;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private static final ZoneId VIETNAM =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy HH:mm",
                    Locale.forLanguageTag("vi-VN")
            );

    private final TemplateEngine templateEngine;

    public byte[] render(InvoiceData invoice) {
        Context context =
                new Context(
                        Locale.forLanguageTag("vi-VN")
                );

        context.setVariable(
                "invoice",
                invoice
        );

        String paidAt =
                invoice.paidAtUtc() == null
                        ? "—"
                        : DATE_TIME_FORMAT.format(
                        invoice.paidAtUtc()
                                .atZone(VIETNAM)
                );

        context.setVariable(
                "paidAt",
                paidAt
        );

        String html =
                templateEngine.process(
                        "invoice/order-invoice",
                        context
                );

        try (
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {
            byte[] regularFont =
                    loadFont(
                            "fonts/NotoSans-Regular.ttf"
                    );

            byte[] boldFont =
                    loadFont(
                            "fonts/NotoSans-Bold.ttf"
                    );

            PdfRendererBuilder builder =
                    new PdfRendererBuilder();

            builder.useFont(
                    () -> new ByteArrayInputStream(
                            regularFont
                    ),
                    "Noto Sans",
                    400,
                    PdfRendererBuilder.FontStyle.NORMAL,
                    true
            );

            builder.useFont(
                    () -> new ByteArrayInputStream(
                            boldFont
                    ),
                    "Noto Sans",
                    700,
                    PdfRendererBuilder.FontStyle.NORMAL,
                    true
            );

            builder.useFastMode();

            builder.withHtmlContent(
                    html,
                    null
            );

            builder.toStream(output);

            builder.run();

            return output.toByteArray();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot generate invoice PDF",
                    exception
            );
        }
    }

    private byte[] loadFont(
            String path
    ) {
        try {
            ClassPathResource resource =
                    new ClassPathResource(path);

            return resource
                    .getInputStream()
                    .readAllBytes();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot load PDF font: " + path,
                    exception
            );
        }
    }
}