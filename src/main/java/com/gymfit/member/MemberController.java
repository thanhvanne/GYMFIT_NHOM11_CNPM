package com.gymfit.member;

import com.gymfit.common.security.SecurityContextService;
import com.gymfit.member.dto.MemberCreateRequest;
import com.gymfit.member.dto.MemberResponse;
import com.gymfit.member.dto.MemberUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public MemberResponse create(
            @Valid @RequestBody MemberCreateRequest request
    ) {
        return memberService.create(
                securityContextService.principal(),
                request
        );
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