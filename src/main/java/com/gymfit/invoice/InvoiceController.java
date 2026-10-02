package com.gymfit.invoice;

import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.SecurityContextService;
import com.gymfit.invoice.dto.InvoiceData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final SecurityContextService securityContextService;

    @GetMapping(
            value = "/{orderId}/invoice",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> invoice(
            @PathVariable Long orderId
    ) {
        AppPrincipal principal =
                securityContextService.principal();

        InvoiceData invoice =
                invoiceService.getInvoice(
                        principal,
                        orderId
                );

        byte[] pdf =
                invoicePdfService.render(invoice);

        String filename =
                "GYMFIT-" + invoice.orderCode() + ".pdf";

        ContentDisposition disposition =
                ContentDisposition
                        .attachment()
                        .filename(
                                filename,
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .contentType(
                        MediaType.APPLICATION_PDF
                )
                .contentLength(pdf.length)
                .body(pdf);
    }
}