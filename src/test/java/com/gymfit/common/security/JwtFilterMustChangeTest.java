package com.gymfit.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import com.gymfit.user.RoleCode;
import com.gymfit.user.UserStatus;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F4 – chặn {@code password_change_required} ở {@link JwtAuthenticationFilter}.
 */
class JwtFilterMustChangeTest {

    private JwtService jwtService;
    private AppUserRepository appUserRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtService = mock(JwtService.class);
        appUserRepository = mock(AppUserRepository.class);

        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();

        filter = new JwtAuthenticationFilter(
                jwtService,
                appUserRepository,
                objectMapper
        );

        when(jwtService.extractUserId("du.thi.gia.token"))
                .thenReturn(7L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Còn nợ đổi mật khẩu gọi API khác → 403 password_change_required, không đi tiếp")
    void biChon403() throws Exception {

        givenUser(true);

        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response =
                run("/api/v1/bookings", chain);

        assertEquals(403, response.getStatus());
        assertNull(
                chain.getRequest(),
                "khong duoc phep di tieu xuong service"
        );

        String body = response.getContentAsString();

        assertTrue(
                body.contains("password_change_required"),
                "body=" + body
        );

        assertTrue(
                body.contains("\"status\":403"),
                "body=" + body
        );

        assertTrue(
                body.contains(
                        "Bạn cần đổi mật khẩu trước khi sử dụng hệ thống"
                ),
                "body=" + body
        );
    }

    @Test
    @DisplayName("Được phép gọi /api/v1/auth/change-password")
    void duocPhepDoiMatKhau() throws Exception {

        givenUser(true);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
                run("/api/v1/auth/change-password", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(
                chain.getRequest(),
                "request phai di tieu xuong controller"
        );
    }

    @Test
    @DisplayName("Được phép gọi /api/v1/auth/me (client đọc cờ mustChangePassword)")
    void duocPhepXemMe() throws Exception {

        givenUser(true);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
                run("/api/v1/auth/me", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("Trang HTML vẫn tải được khi còn nợ đổi mật khẩu")
    void trangHtmlKhongBiChan() throws Exception {

        givenUser(true);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
                run("/member/home", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(
                chain.getRequest(),
                "trang HTML phai tai duoc de hien form doi mat khau"
        );
    }

    @Test
    @DisplayName("Đã đổi mật khẩu (cờ=false) → gọi API bình thường")
    void daDoiMatKhau() throws Exception {

        givenUser(false);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
                run("/api/v1/bookings", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertNotNull(
                SecurityContextHolder.getContext()
                        .getAuthentication(),
                "phai van xac thuc duoc"
        );
    }

    @Test
    @DisplayName("Không có token → cho qua như cũ (Spring xử lý sau)")
    void khongCoToken() throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/bookings"
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    // ------------------------------------------------------------------

    private void givenUser(boolean mustChangePassword) {

        AppUser user = AppUser.builder()
                .id(7L)
                .fullName("Hoi vien mot")
                .email("member1@gymfit.local")
                .passwordHash("hash")
                .roleCode(RoleCode.MEMBER)
                .status(UserStatus.ACTIVE)
                .memberId(1L)
                .mustChangePassword(mustChangePassword)
                .build();

        when(appUserRepository.findById(7L))
                .thenReturn(Optional.of(user));
    }

    private MockHttpServletResponse run(
            String path,
            FilterChain chain
    ) throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", path);

        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer du.thi.gia.token"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        return response;
    }
}
