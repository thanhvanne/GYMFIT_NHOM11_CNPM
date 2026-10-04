package com.gymfit.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F7 – trang đổi mật khẩu bắt buộc.
 *
 * <p>Chứng minh 2 điều: (1) {@code GET /change-password} có route và render
 * đúng form; (2) trang nằm trong {@code permitAll} của {@code SecurityConfig}
 * nên tài khoản chưa đăng nhập vẫn mở được (nếu thiếu, Spring trả 403 và
 * hội viên mới không với tới được form – lỗi thường gặp ở mục 8).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /change-password mở được khi CHƯA đăng nhập (permitAll)")
    void changePasswordPageIsPublic() throws Exception {
        mockMvc.perform(get("/change-password"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Trang render form đổi mật khẩu 3 trường + nút Đăng xuất")
    void changePasswordPageRendersForm() throws Exception {
        mockMvc.perform(get("/change-password"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                org.hamcrest.Matchers
                                        .containsString(
                                                "change-password-form"
                                        )
                        )
                )
                .andExpect(
                        content().string(
                                org.hamcrest.Matchers
                                        .containsString(
                                                "current-password"
                                        )
                        )
                )
                .andExpect(
                        content().string(
                                org.hamcrest.Matchers
                                        .containsString(
                                                "confirm-password"
                                        )
                        )
                );
    }

    @Test
    @DisplayName("Trang đăng nhập vẫn render chú thích mật khẩu tạm (F7)")
    void LoginPageMentionsTemporaryPassword() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                org.hamcrest.Matchers
                                        .containsString(
                                                "mật khẩu tạm"
                                        )
                        )
                );
    }
}
