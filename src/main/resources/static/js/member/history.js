let bookings = [];
let checkIns = [];
let orders = [];
let branches = [];
let facilities = [];

let activeTab = "booking";

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user =
            Auth.requireRole("MEMBER");

        if (!user) {
            return;
        }

        bindTabs();

        try {
            const results =
                await Promise.all([
                    Api.get("/api/v1/bookings"),
                    Api.get("/api/v1/checkins"),
                    Api.get("/api/v1/orders"),
                    Api.get("/api/v1/branches"),
                    Api.get("/api/v1/facilities")
                ]);

            bookings = results[0];
            checkIns = results[1];
            orders = results[2];
            branches = results[3];
            facilities = results[4];

            render();
        } catch (error) {
            showError(
                error.message
                || "Không tải được lịch sử"
            );
        }
    }
);

function bindTabs() {
    document.querySelectorAll(
        ".member-tab"
    ).forEach(button => {
        button.addEventListener(
            "click",
            () => {
                activeTab =
                    button.dataset.tab;

                document.querySelectorAll(
                    ".member-tab"
                ).forEach(item =>
                    item.classList.remove(
                        "active"
                    )
                );

                button.classList.add(
                    "active"
                );

                render();
            }
        );
    });
}

function render() {
    if (activeTab === "booking") {
        renderBookings();
    } else if (
        activeTab === "checkin"
    ) {
        renderCheckIns();
    } else {
        renderOrders();
    }
}

function renderBookings() {
    const container = content();
    container.replaceChildren();

    if (!bookings.length) {
        appendEmpty(
            container,
            "Chưa có booking"
        );
        return;
    }

    bookings.forEach(item => {
        container.appendChild(
            historyItem(
                item.serviceCode,
                `${branchName(item.branchId)} · `
                + facilityName(
                    item.facilityId
                ),
                formatDateTime(
                    item.startsAtUtc
                ),
                item.status
            )
        );
    });
}

function renderCheckIns() {
    const container = content();
    container.replaceChildren();

    if (!checkIns.length) {
        appendEmpty(
            container,
            "Chưa có check-in"
        );
        return;
    }

    checkIns.forEach(item => {
        container.appendChild(
            historyItem(
                item.serviceCode,
                branchName(
                    item.branchId
                ),
                formatDateTime(
                    item.createdAtUtc
                ),
                item.result
            )
        );
    });
}

function renderOrders() {
    const container = content();
    container.replaceChildren();

    if (!orders.length) {
        appendEmpty(
            container,
            "Chưa có giao dịch"
        );
        return;
    }

    orders.forEach(order => {
        container.appendChild(
            historyItem(
                order.orderCode,
                formatMoney(
                    order.total
                ),
                formatDateTime(
                    order.createdAtUtc
                ),
                order.status
            )
        );
    });
}

function historyItem(
    title,
    secondary,
    time,
    status
) {
    const row =
        document.createElement("div");

    row.className =
        "member-list-item";

    const info =
        document.createElement("div");

    info.className =
        "member-list-main";

    const strong =
        document.createElement("strong");

    strong.textContent = title;

    const detail =
        document.createElement("span");

    detail.className =
        "muted small";

    detail.textContent =
        secondary;

    const date =
        document.createElement("span");

    date.className =
        "muted small";

    date.textContent = time;

    info.append(
        strong,
        detail,
        date
    );

    const badge =
        document.createElement("span");

    badge.className =
        statusClass(status);

    badge.textContent =
        status;

    row.append(
        info,
        badge
    );

    return row;
}

function statusClass(status) {
    if (
        status === "CONFIRMED"
        || status === "PAID"
        || status === "ACCEPTED"
    ) {
        return "badge badge-success";
    }

    if (
        status === "CANCELLED"
        || status === "REJECTED"
        || status === "FAILED"
    ) {
        return "badge badge-danger";
    }

    return "badge badge-muted";
}

function branchName(id) {
    const branch =
        branches.find(
            item => item.id === id
        );

    return branch
        ? branch.name
        : `Branch #${id}`;
}

function facilityName(id) {
    const facility =
        facilities.find(
            item => item.id === id
        );

    return facility
        ? facility.name
        : `Facility #${id}`;
}

function content() {
    return document.getElementById(
        "history-content"
    );
}

function appendEmpty(
    container,
    text
) {
    const empty =
        document.createElement("div");

    empty.className = "empty-state";
    empty.textContent = text;

    container.appendChild(empty);
}

function formatMoney(value) {
    return new Intl.NumberFormat(
        "vi-VN",
        {
            style: "currency",
            currency: "VND"
        }
    ).format(Number(value || 0));
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