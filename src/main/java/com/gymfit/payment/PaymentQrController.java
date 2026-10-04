package com.gymfit.payment;

import com.gymfit.checkin.QrImageService;
import com.gymfit.common.error.ConflictException;
import com.gymfit.common.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentQrController {

    private final PaymentService paymentService;
    private final SecurityContextService securityContextService;
    private final QrImageService qrImageService;

    @GetMapping(
            value = "/{id}/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> qr(@PathVariable Long id) {
        var payment = paymentService.get(
                securityContextService.principal(),
                id
        );

        if (payment.provider() != PaymentProviderCode.MOMO_SIMULATOR
                || payment.status() != PaymentStatus.PENDING) {
            throw new ConflictException(
                    "payment_qr_unavailable",
                    "QR chỉ dành cho giao dịch MoMo mô phỏng đang chờ thanh toán"
            );
        }

        // Dữ liệu demo, không phải liên kết chuyển tiền hoặc QR MoMo thật.
        String payload = "GYMFIT_DEMO_ONLY|MOMO_SIMULATOR|"
                + payment.paymentCode()
                + "|ORDER=" + payment.orderId()
                + "|VND=" + payment.amount().toPlainString();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImageService.createPng(payload));
    }
}