let branches = [];
let members = [];
let checkIns = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        await Promise.all([
            loadBranches(),
            loadMembers()
        ]);

        await loadCheckIns();
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
        "manual-branch"
    ).addEventListener(
        "change",
        () => loadServices(
            "manual-branch",
            "manual-service"
        )
    );

    document.getElementById(
        "qr-branch"
    ).addEventListener(
        "change",
        () => loadServices(
            "qr-branch",
            "qr-service"
        )
    );

    document.getElementById(
        "manual-checkin-form"
    ).addEventListener(
        "submit",
        manualCheckIn
    );

    document.getElementById(
        "qr-checkin-form"
    ).addEventListener(
        "submit",
        qrCheckIn
    );

    document.getElementById(
        "refresh-checkins"
    ).addEventListener(
        "click",
        loadCheckIns
    );
}

async function loadBranches() {
    branches = await Api.get(
        "/api/v1/branches?status=ACTIVE"
    );

    for (
        const id of ["manual-branch", "qr-branch"]
        ) {
        const select =
            document.getElementById(id);

        select.replaceChildren();

        branches.forEach(branch => {
            const option =
                document.createElement("option");

            option.value = branch.id;
            option.textContent = branch.name;

            select.appendChild(option);
        });
    }

    if (branches.length) {
        await Promise.all([
            loadServices(
                "manual-branch",
                "manual-service"
            ),
            loadServices(
                "qr-branch",
                "qr-service"
            )
        ]);
    }
}

async function loadMembers() {
    members = await Api.get(
        "/api/v1/members"
    );
}

async function loadServices(
    branchSelectId,
    serviceSelectId
) {
    const branchId =
        document.getElementById(
            branchSelectId
        ).value;

    const target =
        document.getElementById(
            serviceSelectId
        );

    target.replaceChildren();

    if (!branchId) {
        return;
    }

    const services = await Api.get(
        `/api/v1/branches/${branchId}/services`
    );

    services.forEach(service => {
        const option =
            document.createElement("option");

        option.value =
            service.serviceCode;

        option.textContent =
            service.serviceCode;

        target.appendChild(option);
    });
}

async function manualCheckIn(event) {
    event.preventDefault();

    try {
        const result = await Api.post(
            "/api/v1/checkins/manual",
            {
                branchId: Number(
                    document.getElementById(
                        "manual-branch"
                    ).value
                ),

                memberIdentifier:
                    document.getElementById(
                        "manual-member"
                    ).value.trim(),

                serviceCode:
                document.getElementById(
                    "manual-service"
                ).value
            }
        );

        showCheckInResult(result);

        document.getElementById(
            "manual-member"
        ).value = "";

        await loadCheckIns();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

async function qrCheckIn(event) {
    event.preventDefault();

    try {
        const result = await Api.post(
            "/api/v1/checkins/qr",
            {
                branchId: Number(
                    document.getElementById(
                        "qr-branch"
                    ).value
                ),

                token:
                    document.getElementById(
                        "qr-token"
                    ).value.trim(),

                serviceCode:
                document.getElementById(
                    "qr-service"
                ).value
            }
        );

        showCheckInResult(result);

        document.getElementById(
            "qr-token"
        ).value = "";

        await loadCheckIns();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function showCheckInResult(result) {
    if (result.result === "ACCEPTED") {
        showSuccess(
            "Check-in thành công"
        );
    } else {
        showError(
            `Check-in bị từ chối: `
            + reasonText(result.reason)
        );
    }
}

async function loadCheckIns() {
    try {
        checkIns = await Api.get(
            "/api/v1/checkins"
        );

        renderCheckIns();
    } catch (error) {
        showError(error.message);
    }
}

function renderCheckIns() {
    const body = document.getElementById(
        "checkin-table-body"
    );

    body.replaceChildren();

    if (!checkIns.length) {
        appendEmptyRow(
            body,
            7,
            "Chưa có lịch sử check-in"
        );
        return;
    }

    checkIns.slice(0, 100)
        .forEach(item => {
            const row =
                document.createElement("tr");

            appendCell(
                row,
                formatDateTime(
                    item.createdAtUtc
                )
            );

            appendCell(
                row,
                memberName(item.memberId)
            );

            appendCell(
                row,
                branchName(item.branchId)
            );

            appendCell(
                row,
                item.serviceCode
            );

            appendCell(
                row,
                item.method === "QR"
                    ? "QR"
                    : "Thủ công"
            );

            const resultCell =
                document.createElement("td");

            const badge =
                document.createElement("span");

            badge.className =
                item.result === "ACCEPTED"
                    ? "badge badge-success"
                    : "badge badge-danger";

            badge.textContent =
                item.result === "ACCEPTED"
                    ? "Chấp nhận"
                    : "Từ chối";

            resultCell.appendChild(badge);
            row.appendChild(resultCell);

            appendCell(
                row,
                item.reason
                    ? reasonText(item.reason)
                    : "—"
            );

            body.appendChild(row);
        });
}

function reasonText(reason) {
    const values = {
        MEMBER_NOT_FOUND:
            "Không tìm thấy hội viên",

        MEMBER_INACTIVE:
            "Hội viên không hoạt động",

        BRANCH_INACTIVE:
            "Chi nhánh không hoạt động",

        SERVICE_NOT_SUPPORTED:
            "Dịch vụ không được hỗ trợ",

        NO_ACTIVE_MEMBERSHIP:
            "Không có membership hợp lệ",

        SERVICE_NOT_INCLUDED:
            "Gói không bao gồm dịch vụ",

        MEMBERSHIP_BRANCH_MISMATCH:
            "Membership khác chi nhánh",

        DUPLICATE_CHECKIN:
            "Check-in trùng trong 5 phút",

        QR_REPLAY:
            "QR đã được sử dụng",

        QR_EXPIRED_OR_INVALID:
            "QR hết hạn hoặc không hợp lệ"
    };

    return values[reason] || reason;
}

function memberName(id) {
    if (id == null) {
        return "—";
    }

    const member = members.find(
        item => item.id === id
    );

    return member
        ? member.fullName
        : `#${id}`;
}

function branchName(id) {
    const branch = branches.find(
        item => item.id === id
    );

    return branch
        ? branch.name
        : `#${id}`;
}

function formatDateTime(value) {
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

function showSuccess(message) {
    const box = document.getElementById(
        "page-success"
    );

    box.textContent = message;
    box.classList.remove("hidden");

    setTimeout(
        () => box.classList.add("hidden"),
        3000
    );
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

    return error.message || "Có lỗi xảy ra";
}