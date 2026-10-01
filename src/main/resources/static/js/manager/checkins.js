let currentUser;
let members = [];
let services = [];
let checkIns = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser =
            Auth.requireRole(
                "BRANCH_MANAGER"
            );

        if (!currentUser) {
            return;
        }

        bindEvents();

        try {
            const [
                branch,
                memberData,
                serviceData,
                checkInData
            ] = await Promise.all([
                Api.get(
                    `/api/v1/branches/${currentUser.branchId}`
                ),
                Api.get(
                    "/api/v1/members"
                ),
                Api.get(
                    `/api/v1/branches/${currentUser.branchId}/services`
                ),
                Api.get(
                    "/api/v1/checkins"
                )
            ]);

            members = memberData;
            services = serviceData;
            checkIns = checkInData;

            document.getElementById(
                "branch-name"
            ).textContent =
                branch.name;

            renderServices();
            renderCheckIns();
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
        "manual-form"
    ).addEventListener(
        "submit",
        manualCheckIn
    );

    document.getElementById(
        "qr-form"
    ).addEventListener(
        "submit",
        qrCheckIn
    );
}

function renderServices() {
    for (
        const id of [
        "manual-service",
        "qr-service"
    ]
        ) {
        const select =
            document.getElementById(id);

        select.replaceChildren();

        services.forEach(service => {
            const option =
                document.createElement("option");

            option.value =
                service.serviceCode;

            option.textContent =
                service.serviceCode;

            select.appendChild(option);
        });
    }
}

async function manualCheckIn(event) {
    event.preventDefault();

    try {
        const result = await Api.post(
            "/api/v1/checkins/manual",
            {
                memberIdentifier:
                    document.getElementById(
                        "member-identifier"
                    ).value.trim(),

                serviceCode:
                document.getElementById(
                    "manual-service"
                ).value
            }
        );

        showResult(result);

        document.getElementById(
            "member-identifier"
        ).value = "";

        await reloadCheckIns();
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

        showResult(result);

        document.getElementById(
            "qr-token"
        ).value = "";

        await reloadCheckIns();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function showResult(result) {
    if (result.result === "ACCEPTED") {
        showSuccess(
            "Check-in thành công"
        );
    } else {
        showError(
            `Từ chối: `
            + reasonText(result.reason)
        );
    }
}

async function reloadCheckIns() {
    checkIns = await Api.get(
        "/api/v1/checkins"
    );

    renderCheckIns();
}

function renderCheckIns() {
    const body =
        document.getElementById(
            "checkin-body"
        );

    body.replaceChildren();

    if (!checkIns.length) {
        appendEmptyRow(
            body,
            6,
            "Chưa có check-in"
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
                item.serviceCode
            );

            appendCell(
                row,
                item.method
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
                    ? reasonText(
                        item.reason
                    )
                    : "—"
            );

            body.appendChild(row);
        });
}

function reasonText(reason) {
    const map = {
        MEMBER_NOT_FOUND:
            "Không tìm thấy hội viên",

        MEMBER_INACTIVE:
            "Hội viên không hoạt động",

        BRANCH_INACTIVE:
            "Chi nhánh không hoạt động",

        SERVICE_NOT_SUPPORTED:
            "Không hỗ trợ dịch vụ",

        NO_ACTIVE_MEMBERSHIP:
            "Không có membership",

        SERVICE_NOT_INCLUDED:
            "Gói không có dịch vụ",

        MEMBERSHIP_BRANCH_MISMATCH:
            "Sai chi nhánh",

        DUPLICATE_CHECKIN:
            "Check-in trùng",

        QR_REPLAY:
            "QR đã sử dụng",

        QR_EXPIRED_OR_INVALID:
            "QR hết hạn/không hợp lệ"
    };

    return map[reason] || reason;
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

function formatDateTime(value) {
    return new Intl.DateTimeFormat(
        "vi-VN",
        {
            dateStyle: "short",
            timeStyle: "short",
            timeZone:
                "Asia/Ho_Chi_Minh"
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
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}

function showSuccess(message) {
    const box =
        document.getElementById(
            "page-success"
        );

    box.textContent = message;
    box.classList.remove("hidden");

    setTimeout(
        () => box.classList.add(
            "hidden"
        ),
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

    return error.message
        || "Có lỗi xảy ra";
}