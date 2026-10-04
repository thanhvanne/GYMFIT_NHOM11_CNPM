// F7 – trang đổi mật khẩu bắt buộc (tài khoản mustChangePassword=true)
// Mật khẩu chỉ nằm trong RAM của form: không ghi log, không ghi localStorage.

document.addEventListener(
    "DOMContentLoaded",
    () => {
        const user = Auth.user();

        if (!Auth.token() || !user) {
            location.href = "/login";
            return;
        }

        setText(
            "change-password-user",
            user.email || user.fullName || "—"
        );

        if (user.mustChangePassword) {
            document
                .getElementById("change-password-notice")
                .classList.remove("hidden");
        }

        document
            .getElementById("change-password-cancel")
            .addEventListener(
                "click",
                () => Auth.logout()
            );

        const form = document.getElementById(
            "change-password-form"
        );

        const button = document.getElementById(
            "change-password-button"
        );

        form.addEventListener(
            "submit",
            async event => {
                event.preventDefault();

                hide("change-password-error");
                button.disabled = true;
                button.textContent = "Đang đổi...";

                const current = document
                    .getElementById("current-password")
                    .value;

                const next = document
                    .getElementById("new-password")
                    .value;

                const confirm = document
                    .getElementById("confirm-password")
                    .value;

                if (next !== confirm) {
                    showError(
                        "Mật khẩu nhập lại không khớp."
                    );
                    resetButton(button);
                    return;
                }

                try {
                    await Api.post(
                        "/api/v1/auth/change-password",
                        {
                            currentPassword: current,
                            newPassword: next
                        }
                    );
                } catch (error) {
                    showError(messageOf(error));
                    resetButton(button);
                    return;
                }

                // 204 – lấy lại thông tin người dùng
                // (cờ mustChangePassword đã được hạ phía server)
                await finish();
            }
        );
    }
);

async function finish() {
    try {
        const me = await Api.get(
            "/api/v1/auth/me"
        );

        localStorage.setItem(
            Auth.userKey,
            JSON.stringify(me)
        );

        Auth.redirectByRole(me);
    } catch {
        // Không đọc lại được /auth/me nhưng đã đổi
        // thành công → bỏ cờ rồi về trang theo vai trò.
        const user = Auth.user() || {};

        user.mustChangePassword = false;

        localStorage.setItem(
            Auth.userKey,
            JSON.stringify(user)
        );

        Auth.redirectByRole(user);
    }
}

function showError(message) {
    const box = document.getElementById(
        "change-password-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function hide(id) {
    document.getElementById(id)
        .classList.add("hidden");
}

function resetButton(button) {
    button.disabled = false;
    button.textContent = "Đổi mật khẩu";
}

function messageOf(error) {
    if (error.errors
        && Object.keys(error.errors).length) {
        return Object.values(error.errors)
            .join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}

function setText(id, value) {
    document.getElementById(id)
        .textContent = value;
}
