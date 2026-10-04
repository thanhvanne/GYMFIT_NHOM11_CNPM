document.addEventListener(
    "DOMContentLoaded",
    () => {
        const existingUser = Auth.user();

        if (Auth.token() && existingUser) {
            Auth.redirectByRole(existingUser);
            return;
        }

        const form =
            document.getElementById("login-form");

        const errorBox =
            document.getElementById("login-error");

        const button =
            document.getElementById("login-button");

        form.addEventListener(
            "submit",
            async event => {
                event.preventDefault();

                errorBox.classList.add("hidden");
                button.disabled = true;
                button.textContent = "Đang đăng nhập...";

                try {
                    const response = await Api.post(
                        "/api/v1/auth/login",
                        {
                            email: document
                                .getElementById("email")
                                .value
                                .trim(),

                            password: document
                                .getElementById("password")
                                .value
                        }
                    );

                    Auth.save(response);

                    // F7: tài khoản mới (mustChangePassword=true)
                    // → đưa thẳng tới trang đổi mật khẩu
                    if (response.user
                        && response.user
                            .mustChangePassword) {
                        location.href =
                            "/change-password";
                        return;
                    }

                    Auth.redirectByRole(response.user);
                } catch (error) {
                    // F7: bị filter chặn giữa chừng (F4) → đổi mật khẩu đã
                    if (error.code
                        === "password_change_required") {
                        location.href =
                            "/change-password";
                        return;
                    }

                    errorBox.textContent =
                        error.message
                        || "Không thể đăng nhập";

                    errorBox.classList.remove(
                        "hidden"
                    );
                } finally {
                    button.disabled = false;
                    button.textContent = "Đăng nhập";
                }
            }
        );
    }
);