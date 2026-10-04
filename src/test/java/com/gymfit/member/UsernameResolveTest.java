package com.gymfit.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsernameResolveTest {

    @Test
    @DisplayName("Có email → dùng email viết thường")
    void coEmail() {

        assertEquals(
                "member1@gymfit.local",
                MemberAccountService.resolveUsername(
                        " Member1@Gymfit.Local ",
                        "GF00000001"
                )
        );
    }

    @Test
    @DisplayName("Không có email → <mã hội viên>@member.gymfit.local")
    void khongCoEmail() {

        assertEquals(
                "gf00000007@member.gymfit.local",
                MemberAccountService.resolveUsername(
                        null,
                        "GF00000007"
                )
        );

        assertEquals(
                "gf00000007@member.gymfit.local",
                MemberAccountService.resolveUsername(
                        "   ",
                        "GF00000007"
                )
        );
    }

    @Test
    @DisplayName("Mã hội viên thường/trắng đều cho cùng một tên đăng nhập")
    void maHoiVienKhongPhanBietHoaThuong() {

        assertEquals(
                MemberAccountService.resolveUsername(
                        null,
                        "gf00000007"
                ),
                MemberAccountService.resolveUsername(
                        null,
                        "  GF00000007  "
                )
        );
    }

    @Test
    @DisplayName("LOGIN_DOMAIN là hằng dùng chung cho F4 (đăng nhập bằng mã hội viên)")
    void loginDomain() {

        assertEquals(
                "member.gymfit.local",
                MemberAccountService.LOGIN_DOMAIN
        );

        assertEquals(
                "gf00000007@" + MemberAccountService.LOGIN_DOMAIN,
                MemberAccountService.resolveUsername(
                        null,
                        "GF00000007"
                )
        );
    }
}
