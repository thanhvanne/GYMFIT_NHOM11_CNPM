package com.gymfit.auth;

import com.gymfit.auth.dto.CurrentUserResponse;
import com.gymfit.auth.dto.LoginResponse;
import com.gymfit.common.security.AppPrincipal;
import com.gymfit.common.security.JwtService;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;

    public LoginResponse login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        password
                )
        );

        AppUser user = appUserRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow();

        AppPrincipal principal = new AppPrincipal(user);

        return new LoginResponse(
                jwtService.createToken(principal),
                "Bearer",
                toCurrentUser(principal)
        );
    }

    public CurrentUserResponse currentUser(AppPrincipal principal) {
        return toCurrentUser(principal);
    }

    private CurrentUserResponse toCurrentUser(AppPrincipal principal) {
        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getRole(),
                principal.getBranchId(),
                principal.getMemberId()
        );
    }
}