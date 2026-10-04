package com.gymfit.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.common.error.ApiError;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Đường dẫn API được phép gọi khi còn nợ {@code password_change_required}
     * (F4) – nếu thiếu, hội viên sẽ không thể tự đổi mật khẩu.
     */
    private static final Set<String> PASSWORD_CHANGE_ALLOWLIST =
            Set.of(
                    "/api/v1/auth/me",
                    "/api/v1/auth/change-password"
            );

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring(7);
            Long userId = jwtService.extractUserId(token);

            AppUser user = appUserRepository.findById(userId).orElse(null);

            if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                AppPrincipal principal = new AppPrincipal(user);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);

                // D3: chặn mọi API khác cho tới khi đổi mật khẩu xong.
                if (mustChangePassword(user, request.getRequestURI())) {
                    writePasswordChangeRequired(
                            response,
                            request.getRequestURI()
                    );
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Chỉ chặn API ({@code /api/...}); trang HTML/tài nguyên tĩnh
     * ({@code /login}, {@code /member/**}, {@code /css/**}...) vẫn cho qua
     * vì chúng {@code permitAll} và cần tải được để người dùng đổi mật khẩu.
     */
    private boolean mustChangePassword(
            AppUser user,
            String path
    ) {
        return user.isMustChangePassword()
                && path != null
                && path.startsWith("/api/")
                && !PASSWORD_CHANGE_ALLOWLIST.contains(path);
    }

    private void writePasswordChangeRequired(
            HttpServletResponse response,
            String path
    ) throws IOException {

        ApiError body = new ApiError(
                Instant.now(),
                403,
                "password_change_required",
                "Bạn cần đổi mật khẩu trước khi sử dụng hệ thống",
                path,
                Map.of()
        );

        String json;

        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            // Không để hội viên nhận 403 không có body:
            // rơi về JSON ghi tay đúng định dạng ApiError.
            logger.warn(
                    "Khong serialize duoc ApiError password_change_required",
                    exception
            );

            json = FALLBACK_BODY;
        }

        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }

    /**
     * Bản ghi tay khi {@link ObjectMapper} gặp sự cố – giữ đúng định dạng
     * {@link ApiError} để client đọc được {@code code}.
     */
    private static final String FALLBACK_BODY =
            "{\"timestamp\":\"1970-01-01T00:00:00Z\","
                    + "\"status\":403,"
                    + "\"code\":\"password_change_required\","
                    + "\"message\":\"Bạn cần đổi mật khẩu trước khi sử dụng hệ thống\","
                    + "\"path\":\"/api/v1\","
                    + "\"errors\":{}}";
}
