package com.gymfit.member;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.member.dto.AccountCredentialsResponse;
import com.gymfit.member.dto.MemberCreateRequest;
import com.gymfit.member.dto.MemberCreateResponse;
import com.gymfit.member.dto.MemberResponse;
import com.gymfit.member.dto.MemberUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final SecurityContextService securityContextService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MemberResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MemberStatus status
    ) {
        return memberService.list(
                securityContextService.principal(),
                q,
                status
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MemberResponse get(@PathVariable Long id) {
        return memberService.get(
                securityContextService.principal(),
                id
        );
    }

    /**
     * Tạo hội viên; mặc định kèm luôn tài khoản đăng nhập (D2).
     * Response chứa mật khẩu tạm ⇒ {@code Cache-Control: no-store}.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<MemberCreateResponse> create(
            @Valid @RequestBody MemberCreateRequest request
    ) {
        MemberCreateResponse body = memberService.create(
                securityContextService.principal(),
                request
        );

        return ResponseEntity
                .status(201)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    /**
     * Cấp tài khoản cho hội viên đã có (chưa có tài khoản).
     */
    @PostMapping("/{id}/account")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<AccountCredentialsResponse> createAccount(
            @PathVariable Long id
    ) {
        AccountCredentialsResponse body =
                memberService.issueAccount(
                        securityContextService.principal(),
                        id
                );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    /**
     * Đặt lại mật khẩu tài khoản của hội viên.
     */
    @PostMapping("/{id}/account/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<AccountCredentialsResponse> resetAccountPassword(
            @PathVariable Long id
    ) {
        AccountCredentialsResponse body =
                memberService.resetAccountPassword(
                        securityContextService.principal(),
                        id
                );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public MemberResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        return memberService.update(
                securityContextService.principal(),
                id,
                request
        );
    }
}
