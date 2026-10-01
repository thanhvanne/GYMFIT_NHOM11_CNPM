package com.gymfit.common.security;

import com.gymfit.common.error.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextService {

    public AppPrincipal principal() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppPrincipal principal)) {
            throw new UnauthorizedException(
                    "unauthorized",
                    "Bạn chưa đăng nhập"
            );
        }

        return principal;
    }
}