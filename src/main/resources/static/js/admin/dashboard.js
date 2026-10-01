let branches = [];
let members = [];
let orders = [];
let checkIns = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user =
            Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        document.getElementById(
            "user-name"
        ).textContent =
            user.fullName;

        document.getElementById(
            "user-initial"
        ).textContent =
            initial(user.fullName);

        document.getElementById(
            "logout-button"
        ).addEventListener(
            "click",
            () => Auth.logout()
        );

        try {
            const today =
                localDate(
                    new Date()
                );

            const results =
                await Promise.all([
                    Api.get(
                        "/api/v1/reports/dashboard"
                    ),

                    Api.get(
                        `/api/v1/reports/services`
                        + `?from=${today}`
                        + `&to=${today}`
                    ),

                    Api.get(
                        "/api/v1/branches"
                    ),

                    Api.get(
                        "/api/v1/members"
                    ),

                    Api.get(
                        "/api/v1/orders"
                    ),

                    Api.get(
                        "/api/v1/checkins"
                    )
                ]);

            const dashboard =
                results[0];

            const services =
                results[1];

            branches = results[2];
            members = results[3];
            orders = results[4];
            checkIns = results[5];

            renderStats(dashboard);
            renderBranches();
            renderServices(services);
            renderOrders();
            renderCheckIns();
        } catch (error) {
            showError(
                formatApiError(error)
            );
        }
    }
);

function renderStats(data) {
    document.getElementById(
        "stat-revenue"
    ).textContent =
        formatMoney(
            data.revenue
        );

    document.getElementById(
        "stat-members"
    ).textContent =
        formatNumber(
            data.members
        );

    document.getElementById(
        "stat-bookings"
    ).textContent =
        formatNumber(
            data.bookings
        );

    document.getElementById(
        "stat-checkins"
    ).textContent =
        formatNumber(
            data.checkIns
        );

    document.getElementById(
        "active-memberships"
    ).textContent =
        formatNumber(
            data.activeMemberships
        );
}

function renderBranches() {
    const container =
        document.getElementById(
            "branch-overview"
        );

    container.replaceChildren();

    if (!branches.length) {
        container.appendChild(
            emptyElement(
                "Chưa có chi nhánh"
            )
        );

        return;
    }

    branches.forEach(branch => {
        const row =
            document.createElement("a");

        row.href =
            "/admin/branches";

        row.className =
            "dashboard-branch-item";

        const info =
            document.createElement("div");

        const name =
            document.createElement("strong");

        name.textContent =
            branch.name;

        const address =
            document.createElement("div");

        address.className =
            "muted small";

        address.textContent =
            branch.address;

        info.append(
            name,
            address
        );

        const status =
            document.createElement("span");

        status.className =
            branch.status === "ACTIVE"
                ? "badge badge-success"
                : "badge badge-muted";

        status.textContent =
            branch.status === "ACTIVE"
                ? "Hoạt động"
                : "Ngừng";

        row.append(
            info,
            status
        );

        container.appendChild(row);
    });
}

function renderServices(services) {
    const container =
        document.getElementById(
            "service-overview"
        );

    container.replaceChildren();

    services.forEach(service => {
        const row =
            document.createElement("div");

        row.className =
            "dashboard-service-item";

        const left =
            document.createElement("div");

        const name =
            document.createElement("strong");

        name.textContent =
            service.serviceCode;

        const text =
            document.createElement("div");

        text.className =
            "muted small";

        text.textContent =
            `${service.bookings} booking`;

        left.append(
            name,
            text
        );

        const number =
            document.createElement("div");

        number.className =
            "dashboard-service-number";

        number.textContent =
            `${service.checkIns}`;

        const label =
            document.createElement("span");

        label.textContent =
            " check-in";

        number.appendChild(label);

        row.append(
            left,
            number
        );

        container.appendChild(row);
    });
}

function renderOrders() {
    const body =
        document.getElementById(
            "recent-orders-body"
        );

    body.replaceChildren();

    if (!orders.length) {
        appendEmptyRow(
            body,
            6,
            "Chưa có giao dịch"
        );

        return;
    }

    orders.slice(0, 8)
        .forEach(order => {
            const row =
                document.createElement("tr");

            appendCell(
                row,
                order.orderCode
            );

            appendCell(
                row,
                branchName(
                    order.branchId
                )
            );

            appendCell(
                row,
                memberName(
                    order.memberId
                )
            );

            appendCell(
                row,
                formatMoney(
                    order.total
                )
            );

            const statusCell =
                document.createElement("td");

            statusCell.appendChild(
                orderStatusBadge(
                    order.status
                )
            );

            row.appendChild(
                statusCell
            );

            appendCell(
                row,
                formatDateTime(
                    order.createdAtUtc
                )
            );

            body.appendChild(row);
        });
}

function renderCheckIns() {
    const container =
        document.getElementById(
            "recent-checkins"
        );

    container.replaceChildren();

    if (!checkIns.length) {
        container.appendChild(
            emptyElement(
                "Chưa có check-in"
            )
        );

        return;
    }

    checkIns.slice(0, 6)
        .forEach(item => {
            const row =
                document.createElement("div");

            row.className =
                "dashboard-activity-item";

            const info =
                document.createElement("div");

            const title =
                document.createElement("strong");

            title.textContent =
                memberName(
                    item.memberId
                );

            const detail =
                document.createElement("div");

            detail.className =
                "muted small";

            detail.textContent =
                `${item.serviceCode}`
                + ` · ${formatDateTime(
                    item.createdAtUtc
                )}`;

            info.append(
                title,
                detail
            );

            const badge =
                document.createElement("span");

            badge.className =
                item.result === "ACCEPTED"
                    ? "badge badge-success"
                    : "badge badge-danger";

            badge.textContent =
                item.result === "ACCEPTED"
                    ? "Accepted"
                    : "Rejected";

            row.append(
                info,
                badge
            );

            container.appendChild(row);
        });
}

function branchName(id) {
    const branch =
        branches.find(
            item => item.id === id
        );

    return branch
        ? branch.name
        : `#${id}`;
}

function memberName(id) {
    if (id == null) {
        return "Khách lẻ";
    }

    const member =
        members.find(
            item => item.id === id
        );

    return member
        ? member.fullName
        : `#${id}`;
}

function orderStatusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "PAID") {
        badge.classList.add(
            "badge-success"
        );

        badge.textContent =
            "Đã thanh toán";
    } else if (
        status === "CANCELLED"
    ) {
        badge.classList.add(
            "badge-danger"
        );

        badge.textContent =
            "Đã hủy";
    } else {
        badge.classList.add(
            "badge-warning"
        );

        badge.textContent =
            "Chờ thanh toán";
    }

    return badge;
}

function initial(name) {
    return String(name || "A")
        .trim()
        .charAt(0)
        .toUpperCase();
}

function localDate(date) {
    return [
        date.getFullYear(),
        String(
            date.getMonth() + 1
        ).padStart(2, "0"),
        String(
            date.getDate()
        ).padStart(2, "0")
    ].join("-");
}

function formatMoney(value) {
    return new Intl.NumberFormat(
        "vi-VN",
        {
            style: "currency",
            currency: "VND"
        }
    ).format(
        Number(value || 0)
    );
}

function formatNumber(value) {
    return new Intl.NumberFormat(
        "vi-VN"
    ).format(
        Number(value || 0)
    );
}

function formatDateTime(value) {
    if (!value) {
        return "—";
    }

    return new Intl.DateTimeFormat(
        "vi-VN",
        {
            dateStyle: "short",
            timeStyle: "short",
            timeZone:
                "Asia/Ho_Chi_Minh"
        }
    ).format(
        new Date(value)
    );
}

function emptyElement(text) {
    const element =
        document.createElement("div");

    element.className =
        "empty-state";

    element.textContent =
        text;

    return element;
}

function appendCell(row, value) {
    const cell =
        document.createElement("td");

    cell.textContent =
        value ?? "—";

    row.appendChild(cell);
}

function appendEmptyRow(
    body,
    colspan,
    message
) {
    const row =
        document.createElement("tr");

    const cell =
        document.createElement("td");

    cell.colSpan = colspan;
    cell.className =
        "empty-cell";

    cell.textContent =
        message;

    row.appendChild(cell);
    body.appendChild(row);
}

function showError(message) {
    const box =
        document.getElementById(
            "dashboard-error"
        );

    box.textContent = message;

    box.classList.remove(
        "hidden"
    );
}

function formatApiError(error) {
    return error.message
        || "Không tải được dashboard";
}