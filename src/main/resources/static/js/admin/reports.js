let branches = [];
let users = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();
        setDefaultDates();

        try {
            await Promise.all([
                loadBranches(),
                loadUsers()
            ]);

            await Promise.all([
                loadReports(),
                loadAudit()
            ]);
        } catch (error) {
            showError(
                formatApiError(error)
            );
        }
    }
);

function bindEvents() {
    document.getElementById(
        "logout-button"
    ).addEventListener(
        "click",
        () => Auth.logout()
    );

    document.getElementById(
        "load-report-button"
    ).addEventListener(
        "click",
        loadReports
    );

    document.getElementById(
        "refresh-audit-button"
    ).addEventListener(
        "click",
        loadAudit
    );

    document.getElementById(
        "report-branch"
    ).addEventListener(
        "change",
        loadAudit
    );
}

function setDefaultDates() {
    const now = new Date();

    const first = new Date(
        now.getFullYear(),
        now.getMonth(),
        1
    );

    document.getElementById(
        "report-from"
    ).value = localDate(first);

    document.getElementById(
        "report-to"
    ).value = localDate(now);
}

async function loadBranches() {
    branches = await Api.get(
        "/api/v1/branches"
    );

    const select = document.getElementById(
        "report-branch"
    );

    branches.forEach(branch => {
        const option =
            document.createElement("option");

        option.value = String(branch.id);
        option.textContent = branch.name;

        select.appendChild(option);
    });
}

async function loadUsers() {
    users = await Api.get(
        "/api/v1/users"
    );
}

async function loadReports() {
    hide("page-error");

    const from = document.getElementById(
        "report-from"
    ).value;

    const to = document.getElementById(
        "report-to"
    ).value;

    const branchId =
        document.getElementById(
            "report-branch"
        ).value;

    if (!from || !to) {
        showError(
            "Vui lòng chọn khoảng ngày"
        );
        return;
    }

    const query = new URLSearchParams();

    query.set("from", from);
    query.set("to", to);

    if (branchId) {
        query.set("branchId", branchId);
    }

    try {
        const [revenue, services] =
            await Promise.all([
                Api.get(
                    `/api/v1/reports/revenue?${query}`
                ),
                Api.get(
                    `/api/v1/reports/services?${query}`
                )
            ]);

        renderRevenue(revenue);
        renderServices(services);
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function renderRevenue(data) {
    document.getElementById(
        "gross-revenue"
    ).textContent = formatMoney(
        data.grossRevenue
    );

    document.getElementById(
        "plan-revenue"
    ).textContent = formatMoney(
        data.planRevenue
    );

    document.getElementById(
        "product-revenue"
    ).textContent = formatMoney(
        data.productRevenue
    );

    document.getElementById(
        "paid-orders"
    ).textContent = formatNumber(
        data.paidOrders
    );
}

function renderServices(data) {
    const body = document.getElementById(
        "services-report-body"
    );

    body.replaceChildren();

    if (!data.length) {
        appendEmptyRow(
            body,
            3,
            "Không có dữ liệu"
        );
        return;
    }

    data.forEach(item => {
        const row =
            document.createElement("tr");

        appendCell(
            row,
            item.serviceCode
        );

        appendCell(
            row,
            formatNumber(item.bookings)
        );

        appendCell(
            row,
            formatNumber(item.checkIns)
        );

        body.appendChild(row);
    });
}

async function loadAudit() {
    try {
        const branchId =
            document.getElementById(
                "report-branch"
            ).value;

        const url = branchId
            ? `/api/v1/audit?branchId=${branchId}`
            : "/api/v1/audit";

        const events = await Api.get(url);

        renderAudit(events);
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function renderAudit(events) {
    const body = document.getElementById(
        "audit-table-body"
    );

    body.replaceChildren();

    if (!events.length) {
        appendEmptyRow(
            body,
            6,
            "Chưa có audit event"
        );
        return;
    }

    events.slice(0, 200)
        .forEach(event => {
            const row =
                document.createElement("tr");

            appendCell(
                row,
                formatDateTime(
                    event.createdAtUtc
                )
            );

            appendCell(
                row,
                userName(event.actorUserId)
            );

            const actionCell =
                document.createElement("td");

            const badge =
                document.createElement("span");

            badge.className =
                "badge badge-audit";

            badge.textContent =
                event.action;

            actionCell.appendChild(badge);
            row.appendChild(actionCell);

            appendCell(
                row,
                event.entityId == null
                    ? event.entityType
                    : `${event.entityType} #${event.entityId}`
            );

            appendCell(
                row,
                branchName(event.branchId)
            );

            const detailsCell =
                document.createElement("td");

            detailsCell.className =
                "audit-details";

            detailsCell.textContent =
                formatDetails(
                    event.detailsJson
                );

            row.appendChild(detailsCell);

            body.appendChild(row);
        });
}

function formatDetails(json) {
    if (!json) {
        return "—";
    }

    try {
        const value = JSON.parse(json);

        return Object.entries(value)
            .map(
                ([key, item]) =>
                    `${key}: ${item}`
            )
            .join(" · ");
    } catch {
        return json;
    }
}

function userName(id) {
    if (id == null) {
        return "SYSTEM";
    }

    const user = users.find(
        item => item.id === id
    );

    return user
        ? user.fullName
        : `User #${id}`;
}

function branchName(id) {
    if (id == null) {
        return "Toàn hệ thống";
    }

    const branch = branches.find(
        item => item.id === id
    );

    return branch
        ? branch.name
        : `#${id}`;
}

function localDate(date) {
    const year = date.getFullYear();

    const month = String(
        date.getMonth() + 1
    ).padStart(2, "0");

    const day = String(
        date.getDate()
    ).padStart(2, "0");

    return `${year}-${month}-${day}`;
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

function formatNumber(value) {
    return new Intl.NumberFormat(
        "vi-VN"
    ).format(Number(value || 0));
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
            timeZone: "Asia/Ho_Chi_Minh"
        }
    ).format(new Date(value));
}

function appendCell(row, value) {
    const cell =
        document.createElement("td");

    cell.textContent = value ?? "—";

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
    cell.className = "empty-cell";
    cell.textContent = message;

    row.appendChild(cell);
    body.appendChild(row);
}

function showError(message) {
    const box = document.getElementById(
        "page-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function hide(id) {
    document.getElementById(id)
        .classList.add("hidden");
}

function formatApiError(error) {
    if (
        error.errors
        && Object.keys(error.errors).length
    ) {
        return Object.values(
            error.errors
        ).join(". ");
    }

    return error.message
        || "Có lỗi xảy ra";
}