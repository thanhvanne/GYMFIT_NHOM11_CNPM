let currentUser;
let member;
let memberships = [];
let bookings = [];
let checkIns = [];
let plans = [];

// Trang chủ luôn làm việc với danh sách gói, không còn giả định chỉ có một gói.
document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser = Auth.requireRole("MEMBER");

        if (!currentUser) {
            return;
        }

        document.getElementById("logout-button")
            .addEventListener("click", () => Auth.logout());

        try {
            const results = await Promise.all([
                Api.get(`/api/v1/members/${currentUser.memberId}`),
                Api.get("/api/v1/bookings"),
                Api.get("/api/v1/checkins"),
                Api.get(
                    `/api/v1/members/${currentUser.memberId}/memberships/active`
                )
            ]);

            member = results[0];
            bookings = results[1];
            checkIns = results[2];
            memberships = results[3];

            await loadPlans();

            renderMember();
            renderMemberships();
            renderStats();
            renderUpcoming();
        } catch (error) {
            showError(formatApiError(error));
        }
    }
);

async function loadPlans() {
    const ids = [...new Set(memberships.map(item => item.planId))];

    if (!ids.length) {
        plans = [];
        return;
    }

    const loaded = await Promise.all(
        ids.map(async id => {
            try {
                return await Api.get(`/api/v1/plans/${id}`);
            } catch {
                return null;
            }
        })
    );

    plans = loaded.filter(Boolean);
}

function renderMember() {
    document.getElementById("member-name").textContent = member.fullName;
    document.getElementById("member-code").textContent = member.memberCode;
}

function renderMemberships() {
    const container = document.getElementById("membership-list");
    container.replaceChildren();

    if (!memberships.length) {
        container.appendChild(
            emptyElement("Bạn chưa có gói tập đang hoạt động")
        );
        renderServiceGrid([]);
        return;
    }

    memberships.forEach(membership => {
        const plan = plans.find(item => item.id === membership.planId);
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
            services.appendChild(serviceBadge(service));
        });

        card.append(head, dates, branch, services);
        container.appendChild(card);
    });

    renderServiceGrid(
        [...new Set(memberships.flatMap(item => item.services))]
    );
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

function serviceBadge(service) {
    const badge = document.createElement("span");
    badge.className = "member-service-badge";
    badge.textContent = serviceName(service);
    return badge;
}

function renderServiceGrid(serviceCodes) {
    const grid = document.getElementById("service-grid");
    grid.replaceChildren();

    if (!serviceCodes.length) {
        grid.appendChild(
            emptyElement("Bạn chưa có dịch vụ đang hoạt động")
        );
        return;
    }

    serviceCodes.forEach(service => {
        const card = document.createElement("a");
        card.href = `/member/booking?service=${service}`;
        card.className = "member-service-card";

        const icon = document.createElement("div");
        icon.className = "member-service-icon";
        icon.textContent = serviceIcon(service);
        icon.setAttribute("aria-hidden", "true");

        const name = document.createElement("strong");
        name.textContent = serviceName(service);
        card.append(icon, name);
        grid.appendChild(card);
    });
}

function renderStats() {
    const now = new Date();
    const upcoming = bookings.filter(item =>
        item.status === "CONFIRMED"
        && new Date(item.startsAtUtc) > now
    );

    document.getElementById("upcoming-count").textContent = upcoming.length;
    document.getElementById("checkin-count").textContent = checkIns.filter(
        item => item.result === "ACCEPTED"
    ).length;
}

function renderUpcoming() {
    const container = document.getElementById("upcoming-list");
    container.replaceChildren();

    const now = new Date();
    const items = bookings
        .filter(item =>
            item.status === "CONFIRMED"
            && new Date(item.startsAtUtc) > now
        )
        .sort((a, b) => new Date(a.startsAtUtc) - new Date(b.startsAtUtc))
        .slice(0, 3);

    if (!items.length) {
        container.appendChild(emptyElement("Chưa có lịch sắp tới"));
        return;
    }

    items.forEach(item => {
        const row = document.createElement("div");
        row.className = "member-list-item";

        const info = document.createElement("div");
        info.className = "member-list-main";

        const title = document.createElement("strong");
        title.textContent = serviceName(item.serviceCode);

        const time = document.createElement("span");
        time.className = "muted small";
        time.textContent = formatDateTime(item.startsAtUtc);
        info.append(title, time);

        const badge = document.createElement("span");
        badge.className = "badge badge-success";
        badge.textContent = "Đã xác nhận";
        row.append(info, badge);
        container.appendChild(row);
    });
}

function serviceName(service) {
    return {
        GYM: "Gym",
        BOXING: "Boxing",
        PICKLEBALL: "Pickleball"
    }[service] || service;
}

function serviceIcon(service) {
    return {
        GYM: "🏋️",
        BOXING: "🥊",
        PICKLEBALL: "🏓"
    }[service] || "★";
}

function emptyElement(text) {
    const div = document.createElement("div");
    div.className = "empty-state";
    div.textContent = text;
    return div;
}

function formatDate(value) {
    if (!value) return "—";
    const [y, m, d] = value.split("-");
    return `${d}/${m}/${y}`;
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "Asia/Ho_Chi_Minh"
    }).format(new Date(value));
}

function showError(message) {
    const box = document.getElementById("page-error");
    box.textContent = message;
    box.classList.remove("hidden");
}

function formatApiError(error) {
    if (error.errors && Object.keys(error.errors).length) {
        return Object.values(error.errors).join(". ");
    }
    return error.message || "Có lỗi xảy ra";
}
