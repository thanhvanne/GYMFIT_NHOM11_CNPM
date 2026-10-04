document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user =
            Auth.requireRole("MEMBER");

        if (!user) {
            return;
        }

        document.getElementById(
            "logout-button"
        ).addEventListener(
            "click",
            () => Auth.logout()
        );

        // F7.5 – đổi mật khẩu từ hồ sơ
        document.getElementById(
            "profile-password-form"
        ).addEventListener(
            "submit",
            changePassword
        );

        try {
            const member =
                await Api.get(
                    `/api/v1/members/${user.memberId}`
                );

            const branch =
                await Api.get(
                    `/api/v1/branches/${member.homeBranchId}`
                );

            renderMember(
                member,
                branch
            );

            try {
                const membership =
                    await Api.get(
                        `/api/v1/members/${user.memberId}/memberships/current`
                    );

                const plan =
                    await Api.get(
                        `/api/v1/plans/${membership.planId}`
                    );

                renderMembership(
                    membership,
                    plan
                );
            } catch (error) {
                if (error.status === 404) {
                    renderNoMembership();
                } else {
                    throw error;
                }
            }
        } catch (error) {
            showError(
                error.message
                || "Không tải được hồ sơ"
            );
        }
    }
);

function renderMember(
    member,
    branch
) {
    document.getElementById(
        "profile-avatar"
    ).textContent =
        member.fullName
            .trim()
            .charAt(0)
            .toUpperCase();

    document.getElementById(
        "profile-name"
    ).textContent =
        member.fullName;

    document.getElementById(
        "profile-code"
    ).textContent =
        member.memberCode;

    document.getElementById(
        "profile-email"
    ).textContent =
        member.email || "—";

    document.getElementById(
        "profile-phone"
    ).textContent =
        member.phone;

    document.getElementById(
        "profile-branch"
    ).textContent =
        branch.name;

    document.getElementById(
        "profile-dob"
    ).textContent =
        formatDate(
            member.dateOfBirth
        );
}

function renderMembership(
    membership,
    plan
) {
    document.getElementById(
        "profile-membership-name"
    ).textContent =
        plan.name;

    document.getElementById(
        "profile-membership-status"
    ).textContent =
        membership.status;

    document.getElementById(
        "profile-start"
    ).textContent =
        formatDate(
            membership.startDate
        );

    document.getElementById(
        "profile-end"
    ).textContent =
        formatDate(
            membership.endDate
        );
}

function renderNoMembership() {
    document.getElementById(
        "profile-membership-name"
    ).textContent =
        "Chưa có gói tập";

    document.getElementById(
        "profile-membership-status"
    ).textContent =
        "NONE";

    document.getElementById(
        "profile-start"
    ).textContent = "—";

    document.getElementById(
        "profile-end"
    ).textContent = "—";
}

function formatDate(value) {
    if (!value) {
        return "—";
    }

    const [y, m, d] =
        value.split("-");

    return `${d}/${m}/${y}`;
}

function showError(message) {
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}

// ------------------------------------------------------------------
// F7.5 – đổi mật khẩu (cùng API với trang /change-password)
// Mật khẩu chỉ nằm trong ô nhập: không log, không ghi localStorage.
// ------------------------------------------------------------------

async function changePassword(event) {
    event.preventDefault();

    hideBox("profile-password-error");
    hideBox("profile-password-success");

    const button = document.getElementById(
        "profile-password-button"
    );

    button.disabled = true;

    const current = document.getElementById(
        "profile-current-password"
    ).value;

    const next = document.getElementById(
        "profile-new-password"
    ).value;

    const confirm = document.getElementById(
        "profile-confirm-password"
    ).value;

    if (next !== confirm) {
        showPasswordError(
            "Mật khẩu nhập lại không khớp."
        );
        button.disabled = false;
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

        document
            .getElementById("profile-password-form")
            .reset();

        const success = document.getElementById(
            "profile-password-success"
        );

        success.textContent = "Đã đổi mật khẩu.";
        success.classList.remove("hidden");
    } catch (error) {
        showPasswordError(messageOf(error));
    } finally {
        button.disabled = false;
    }
}

function showPasswordError(message) {
    const box = document.getElementById(
        "profile-password-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function hideBox(id) {
    document.getElementById(id)
        .classList.add("hidden");
}

function messageOf(error) {
    if (error.errors
        && Object.keys(error.errors).length) {
        return Object.values(error.errors)
            .join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}