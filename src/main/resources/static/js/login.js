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
                    Auth.redirectByRole(response.user);
                } catch (error) {
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