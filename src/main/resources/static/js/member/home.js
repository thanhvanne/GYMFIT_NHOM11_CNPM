let currentUser;
let member;
let membership;
let bookings = [];
let checkIns = [];
let plans = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser =
            Auth.requireRole("MEMBER");

        if (!currentUser) {
            return;
        }

        document.getElementById(
            "logout-button"
        ).addEventListener(
            "click",
            () => Auth.logout()
        );

        try {
            const results =
                await Promise.all([
                    Api.get(
                        `/api/v1/members/${currentUser.memberId}`
                    ),
                    Api.get(
                        "/api/v1/bookings"
                    ),
                    Api.get(
                        "/api/v1/checkins"
                    )
                ]);

            member = results[0];
            bookings = results[1];
            checkIns = results[2];

            renderMember();

            try {
                membership = await Api.get(
                    `/api/v1/members/${currentUser.memberId}/memberships/current`
                );

                plans = await Api.get(
                    `/api/v1/plans?branchId=${membership.branchId}`
                );

                renderMembership();
            } catch (error) {
                if (error.status === 404) {
                    renderNoMembership();
                } else {
                    throw error;
                }
            }

            renderStats();
            renderUpcoming();
        } catch (error) {
            showError(
                formatApiError(error)
            );
        }
    }
);

function renderMember() {
    document.getElementById(
        "member-name"
    ).textContent =
        member.fullName;

    document.getElementById(
        "member-code"
    ).textContent =
        member.memberCode;
}

function renderMembership() {
    const plan = plans.find(
        value =>
            value.id === membership.planId
    );

    document.getElementById(
        "membership-plan"
    ).textContent =
        plan
            ? plan.name
            : `Gói #${membership.planId}`;

    document.getElementById(
        "membership-status"
    ).textContent =
        membership.status;

    document.getElementById(
        "membership-start"
    ).textContent =
        formatDate(
            membership.startDate
        );

    document.getElementById(
        "membership-end"
    ).textContent =
        formatDate(
            membership.endDate
        );

    const services =
        document.getElementById(
            "membership-services"
        );

    const grid =
        document.getElementById(
            "service-grid"
        );

    services.replaceChildren();
    grid.replaceChildren();

    membership.services.forEach(
        service => {
            const badge =
                document.createElement("span");

            badge.className =
                "member-service-badge";

            badge.textContent =
                service;

            services.appendChild(badge);

            const card =
                document.createElement("a");

            card.href =
                `/member/booking?service=${service}`;

            card.className =
                "member-service-card";

            const icon =
                document.createElement("div");

            icon.className =
                "member-service-icon";

            icon.textContent =
                service.charAt(0);

            const name =
                document.createElement("strong");

            name.textContent =
                serviceName(service);

            card.append(icon, name);
            grid.appendChild(card);
        }
    );
}

function renderNoMembership() {
    document.getElementById(
        "membership-plan"
    ).textContent =
        "Chưa có gói tập";

    document.getElementById(
        "membership-status"
    ).textContent = "NONE";

    document.getElementById(
        "membership-start"
    ).textContent = "—";

    document.getElementById(
        "membership-end"
    ).textContent = "—";

    document.getElementById(
        "membership-services"
    ).replaceChildren();

    const grid =
        document.getElementById(
            "service-grid"
        );

    grid.replaceChildren(
        emptyElement(
            "Bạn chưa có dịch vụ đang hoạt động"
        )
    );
}

function renderStats() {
    const now = new Date();

    const upcoming =
        bookings.filter(
            item =>
                item.status === "CONFIRMED"
                && new Date(
                    item.startsAtUtc
                ) > now
        );

    document.getElementById(
        "upcoming-count"
    ).textContent =
        upcoming.length;

    document.getElementById(
        "checkin-count"
    ).textContent =
        checkIns.filter(
            item =>
                item.result === "ACCEPTED"
        ).length;
}

function renderUpcoming() {
    const container =
        document.getElementById(
            "upcoming-list"
        );

    container.replaceChildren();

    const now = new Date();

    const items = bookings
        .filter(
            item =>
                item.status === "CONFIRMED"
                && new Date(
                    item.startsAtUtc
                ) > now
        )
        .sort(
            (a, b) =>
                new Date(a.startsAtUtc)
                - new Date(b.startsAtUtc)
        )
        .slice(0, 3);

    if (!items.length) {
        container.appendChild(
            emptyElement(
                "Chưa có lịch sắp tới"
            )
        );

        return;
    }

    items.forEach(item => {
        const row =
            document.createElement("div");

        row.className =
            "member-list-item";

        const info =
            document.createElement("div");

        info.className =
            "member-list-main";

        const title =
            document.createElement("strong");

        title.textContent =
            serviceName(
                item.serviceCode
            );

        const time =
            document.createElement("span");

        time.className =
            "muted small";

        time.textContent =
            formatDateTime(
                item.startsAtUtc
            );

        info.append(title, time);

        const badge =
            document.createElement("span");

        badge.className =
            "badge badge-success";

        badge.textContent =
            "Đã xác nhận";

        row.append(info, badge);

        container.appendChild(row);
    });
}

function serviceName(service) {
    const map = {
        GYM: "Gym",
        BOXING: "Boxing",
        PICKLEBALL: "Pickleball"
    };

    return map[service] || service;
}

function emptyElement(text) {
    const div =
        document.createElement("div");

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

function formatDateTime(value) {
    return new Intl.DateTimeFormat(
        "vi-VN",
        {
            dateStyle: "medium",
            timeStyle: "short",
            timeZone: "Asia/Ho_Chi_Minh"
        }
    ).format(new Date(value));
}

function showError(message) {
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}

function formatApiError(error) {
    return error.message
        || "Có lỗi xảy ra";
}