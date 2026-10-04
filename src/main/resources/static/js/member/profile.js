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
            const member = await Api.get(
                `/api/v1/members/${user.memberId}`
            );

            const branch = await Api.get(
                `/api/v1/branches/${member.homeBranchId}`
            );

            renderMember(member, branch);

            const memberships = await Api.get(
                `/api/v1/members/${user.memberId}/memberships/active`
            );

            const plans = await Promise.all(
                memberships.map(item =>
                    Api.get(`/api/v1/plans/${item.planId}`)
                )
            );

            renderMemberships(memberships, plans);
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

function renderMemberships(memberships, plans) {
    const container = document.getElementById("profile-memberships");
    container.replaceChildren();

    if (!memberships.length) {
        container.appendChild(
            emptyElement("Chưa có gói tập đang hoạt động")
        );
        return;
    }

    memberships.forEach((membership, index) => {
        const plan = plans[index];
        const card = document.createElement("article");
        card.className = "member-membership-card";

        const head = document.createElement("div");
        head.className = "member-membership-head";

        const title = document.createElement("div");
        const eyebrow = document.createElement("div");
        eyebrow.className = "member-membership-title";
        eyebrow.textContent = "MEMBERSHIP";
        const name = document.createElement("h3");
        name.className = "member-membership-name";
        name.textContent = plan?.name || `Gói #${membership.planId}`;
        title.append(eyebrow, name);

        const status = document.createElement("span");
        status.className = "badge badge-success";
        status.textContent = membership.status;
        head.append(title, status);

        const dates = document.createElement("div");
        dates.className = "member-membership-dates";
        dates.append(
            dateCell("Bắt đầu", formatDate(membership.startDate)),
            dateCell("Hết hạn", formatDate(membership.endDate))
        );

        const branch = document.createElement("div");
        branch.className = "member-membership-branch";
        branch.textContent = `Chi nhánh #${membership.branchId}`;

        const services = document.createElement("div");
        services.className = "member-services";
        membership.services.forEach(service => {
            const badge = document.createElement("span");
            badge.className = "member-service-badge";
            badge.textContent = serviceName(service);
            services.appendChild(badge);
        });

        card.append(head, dates, branch, services);
        container.appendChild(card);
    });
}

function dateCell(label, value) {
    const cell = document.createElement("div");
    const labelElement = document.createElement("span");
    labelElement.textContent = label;
    const valueElement = document.createElement("strong");
    valueElement.textContent = value;
    cell.append(labelElement, valueElement);
    return cell;
}

function serviceName(service) {
    return {
        GYM: "Gym",
        BOXING: "Boxing",
        PICKLEBALL: "Pickleball"
    }[service] || service;
}

function emptyElement(text) {
    const div = document.createElement("div");
    div.className = "empty-state";
    div.textContent = text;
    return div;
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