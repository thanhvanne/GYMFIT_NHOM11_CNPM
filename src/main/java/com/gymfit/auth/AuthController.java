package com.gymfit.auth;

import com.gymfit.auth.dto.CurrentUserResponse;
import com.gymfit.auth.dto.LoginRequest;
import com.gymfit.auth.dto.LoginResponse;
import com.gymfit.common.security.SecurityContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityContextService securityContextService;

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(
                request.email(),
                request.password()
        );
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return authService.currentUser(
                securityContextService.principal()
        );
    }
}