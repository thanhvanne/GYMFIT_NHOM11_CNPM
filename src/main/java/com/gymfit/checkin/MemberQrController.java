package com.gymfit.checkin;

import com.gymfit.checkin.dto.QrResponse;
import com.gymfit.common.error.ForbiddenException;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/v1/member/qr")
@RequiredArgsConstructor
public class MemberQrController {

    private final QrTokenService qrTokenService;
    private final QrImageService qrImageService;
    private final SecurityContextService securityContextService;

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public QrResponse issue() {
        AppPrincipal principal =
                securityContextService.principal();

        if (principal.getMemberId() == null) {
            throw new ForbiddenException(
                    "member_scope_missing",
                    "Tài khoản chưa liên kết hội viên"
            );
        }

        QrTokenService.IssuedQrToken issued =
                qrTokenService.issue(
                        principal.getMemberId()
                );

        byte[] png =
                qrImageService.createPng(
                        issued.token()
                );

        String imageDataUrl =
                "data:image/png;base64,"
                        + Base64.getEncoder()
                        .encodeToString(png);

        return new QrResponse(
                issued.token(),
                imageDataUrl,
                issued.expiresAtUtc()
        );
    }
}