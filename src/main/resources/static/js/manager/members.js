let currentUser = null;
let currentBranch = null;
let members = [];

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
            currentBranch = await Api.get(
                `/api/v1/branches/${currentUser.branchId}`
            );

            await loadMembers();
        } catch (error) {
            showPageError(
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
        "create-member-button"
    ).addEventListener(
        "click",
        openCreateMember
    );

    document.getElementById(
        "member-modal-close"
    ).addEventListener(
        "click",
        closeMemberModal
    );

    document.getElementById(
        "member-cancel-button"
    ).addEventListener(
        "click",
        closeMemberModal
    );

    document.getElementById(
        "member-form"
    ).addEventListener(
        "submit",
        saveMember
    );

    document.getElementById(
        "member-search"
    ).addEventListener(
        "input",
        renderMembers
    );

    document.getElementById(
        "member-status-filter"
    ).addEventListener(
        "change",
        renderMembers
    );

    document.getElementById(
        "membership-close"
    ).addEventListener(
        "click",
        () => hide("membership-modal")
    );
}

async function loadMembers() {
    members = await Api.get(
        "/api/v1/members"
    );

    renderMembers();
}

function renderMembers() {
    const body = document.getElementById(
        "member-table-body"
    );

    body.replaceChildren();

    const query = document.getElementById(
        "member-search"
    ).value.trim().toLowerCase();

    const status = document.getElementById(
        "member-status-filter"
    ).value;

    const filtered = members.filter(
        member => {
            const queryMatch =
                !query
                || [
                    member.memberCode,
                    member.fullName,
                    member.phone,
                    member.email
                ].some(
                    value =>
                        String(value || "")
                            .toLowerCase()
                            .includes(query)
                );

            return queryMatch
                && (
                    !status
                    || member.status === status
                );
        }
    );

    if (!filtered.length) {
        appendEmptyRow(
            body,
            6,
            "Không có hội viên"
        );

        return;
    }

    filtered.forEach(member => {
        const row =
            document.createElement("tr");

        appendCell(
            row,
            member.memberCode
        );

        appendCell(
            row,
            member.fullName
        );

        appendCell(
            row,
            member.phone
        );

        appendCell(
            row,
            member.email || "—"
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            statusBadge(member.status)
        );

        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        const membership =
            actionButton("Membership");

        membership.addEventListener(
            "click",
            () => openMembership(member)
        );

        const edit =
            actionButton("Sửa");

        edit.addEventListener(
            "click",
            () => openEditMember(member)
        );

        actions.append(
            membership,
            edit
        );

        row.appendChild(actions);

        body.appendChild(row);
    });
}

function openCreateMember() {
    document.getElementById(
        "member-form"
    ).reset();

    document.getElementById(
        "member-id"
    ).value = "";

    document.getElementById(
        "member-modal-title"
    ).textContent =
        "Thêm hội viên";

    document.getElementById(
        "member-branch-text"
    ).textContent =
        currentBranch.name;

    document.getElementById(
        "member-status-field"
    ).classList.add("hidden");

    hide("member-form-error");
    show("member-modal");
}

function openEditMember(member) {
    document.getElementById(
        "member-id"
    ).value = member.id;

    document.getElementById(
        "member-modal-title"
    ).textContent =
        "Sửa hội viên";

    document.getElementById(
        "member-branch-text"
    ).textContent =
        currentBranch.name;

    document.getElementById(
        "member-name"
    ).value = member.fullName;

    document.getElementById(
        "member-phone"
    ).value = member.phone;

    document.getElementById(
        "member-email"
    ).value = member.email || "";

    document.getElementById(
        "member-dob"
    ).value =
        member.dateOfBirth || "";

    document.getElementById(
        "member-status"
    ).value = member.status;

    document.getElementById(
        "member-status-field"
    ).classList.remove("hidden");

    hide("member-form-error");
    show("member-modal");
}

async function saveMember(event) {
    event.preventDefault();

    const id = document.getElementById(
        "member-id"
    ).value;

    const common = {
        fullName:
            document.getElementById(
                "member-name"
            ).value.trim(),

        phone:
            document.getElementById(
                "member-phone"
            ).value.trim(),

        email: nullable(
            document.getElementById(
                "member-email"
            ).value
        ),

        homeBranchId:
        currentUser.branchId,

        dateOfBirth: nullable(
            document.getElementById(
                "member-dob"
            ).value
        )
    };

    try {
        if (id) {
            await Api.put(
                `/api/v1/members/${id}`,
                {
                    ...common,

                    status:
                    document.getElementById(
                        "member-status"
                    ).value
                }
            );

            showSuccess(
                "Đã cập nhật hội viên"
            );
        } else {
            await Api.post(
                "/api/v1/members",
                common
            );

            showSuccess(
                "Đã tạo hội viên"
            );
        }

        closeMemberModal();
        await loadMembers();
    } catch (error) {
        const box = document.getElementById(
            "member-form-error"
        );

        box.textContent =
            formatApiError(error);

        box.classList.remove("hidden");
    }
}

async function openMembership(member) {
    document.getElementById(
        "membership-title"
    ).textContent =
        `Membership - ${member.fullName}`;

    document.getElementById(
        "current-membership"
    ).textContent =
        "Đang tải...";

    show("membership-modal");

    try {
        const history = await Api.get(
            `/api/v1/members/${member.id}/memberships`
        );

        renderMembershipHistory(
            history
        );

        try {
            const current = await Api.get(
                `/api/v1/members/${member.id}/memberships/current`
            );

            renderCurrentMembership(
                current
            );
        } catch (error) {
            if (error.status === 404) {
                document.getElementById(
                    "current-membership"
                ).textContent =
                    "Chưa có membership đang hoạt động";
            } else {
                throw error;
            }
        }
    } catch (error) {
        document.getElementById(
            "current-membership"
        ).textContent =
            formatApiError(error);
    }
}

function renderCurrentMembership(item) {
    const container =
        document.getElementById(
            "current-membership"
        );

    container.replaceChildren();

    const summary =
        document.createElement("div");

    summary.className =
        "membership-summary";

    const title =
        document.createElement("strong");

    title.textContent =
        `Membership #${item.id}`;

    const date =
        document.createElement("div");

    date.textContent =
        `${formatDate(item.startDate)}`
        + ` - ${formatDate(item.endDate)}`;

    const services =
        document.createElement("div");

    services.className =
        "service-badges";

    item.services.forEach(service => {
        const badge =
            document.createElement("span");

        badge.className =
            "badge badge-yellow";

        badge.textContent = service;

        services.appendChild(badge);
    });

    summary.append(
        title,
        date,
        services
    );

    container.appendChild(summary);
}

function renderMembershipHistory(items) {
    const body = document.getElementById(
        "membership-history"
    );

    body.replaceChildren();

    if (!items.length) {
        appendEmptyRow(
            body,
            5,
            "Chưa có lịch sử"
        );

        return;
    }

    items.forEach(item => {
        const row =
            document.createElement("tr");

        appendCell(row, `#${item.id}`);
        appendCell(row, `#${item.planId}`);

        appendCell(
            row,
            formatDate(item.startDate)
        );

        appendCell(
            row,
            formatDate(item.endDate)
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            statusBadge(item.status)
        );

        row.appendChild(statusCell);

        body.appendChild(row);
    });
}

function statusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "ACTIVE") {
        badge.classList.add(
            "badge-success"
        );
    } else if (
        status === "REPLACED"
    ) {
        badge.classList.add(
            "badge-warning"
        );
    } else {
        badge.classList.add(
            "badge-muted"
        );
    }

    badge.textContent = status;

    return badge;
}

function formatDate(value) {
    if (!value) {
        return "—";
    }

    const [
        year,
        month,
        day
    ] = value.split("-");

    return `${day}/${month}/${year}`;
}

function nullable(value) {
    const result =
        String(value || "").trim();

    return result || null;
}

function actionButton(text) {
    const button =
        document.createElement("button");

    button.type = "button";

    button.className =
        "button button-small button-secondary";

    button.textContent = text;

    return button;
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

function closeMemberModal() {
    hide("member-modal");
}

function show(id) {
    document.getElementById(id)
        .classList.remove("hidden");
}

function hide(id) {
    document.getElementById(id)
        .classList.add("hidden");
}

function showPageError(message) {
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