let members = [];
let branches = [];

// F6: thông tin đăng nhập chỉ nằm trong RAM – không ghi localStorage/log
let credentialsPassword = null;
let credentialsPayload = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        await loadBranches();
        await loadMembers();
    }
);

function bindEvents() {
    document
        .getElementById("logout-button")
        .addEventListener(
            "click",
            () => Auth.logout()
        );

    document
        .getElementById("create-member-button")
        .addEventListener(
            "click",
            openCreateMember
        );

    document
        .getElementById("member-modal-close")
        .addEventListener(
            "click",
            closeMemberModal
        );

    document
        .getElementById("member-cancel-button")
        .addEventListener(
            "click",
            closeMemberModal
        );

    document
        .getElementById("member-form")
        .addEventListener(
            "submit",
            saveMember
        );

    document
        .getElementById("member-search")
        .addEventListener(
            "input",
            renderMembers
        );

    document
        .getElementById("member-status-filter")
        .addEventListener(
            "change",
            loadMembers
        );

    document
        .getElementById("membership-modal-close")
        .addEventListener(
            "click",
            closeMembershipModal
        );

    // F6: hộp thoại Copy / In phiếu thông tin đăng nhập
    document
        .getElementById("credentials-modal-close")
        .addEventListener(
            "click",
            closeCredentials
        );

    document
        .getElementById("credentials-dismiss")
        .addEventListener(
            "click",
            closeCredentials
        );

    document
        .getElementById("credentials-copy")
        .addEventListener(
            "click",
            copyCredentials
        );

    document
        .getElementById("credentials-print")
        .addEventListener(
            "click",
            printCredentials
        );
}

async function loadBranches() {
    try {
        branches = await Api.get(
            "/api/v1/branches"
        );

        renderBranchOptions();
    } catch (error) {
        showError(
            "Không tải được danh sách chi nhánh: "
            + error.message
        );
    }
}

function renderBranchOptions() {
    const select = document.getElementById(
        "member-home-branch"
    );

    select.replaceChildren();

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent =
        "Chọn chi nhánh";

    select.appendChild(placeholder);

    branches.forEach(branch => {
        const option =
            document.createElement("option");

        option.value = String(branch.id);
        option.textContent =
            branch.status === "ACTIVE"
                ? branch.name
                : `${branch.name} (Ngừng hoạt động)`;

        select.appendChild(option);
    });
}

async function loadMembers() {
    clearPageError();

    const status = document
        .getElementById("member-status-filter")
        .value;

    try {
        const url = status
            ? `/api/v1/members?status=${encodeURIComponent(status)}`
            : "/api/v1/members";

        members = await Api.get(url);

        renderMembers();
    } catch (error) {
        showError(error.message);
    }
}

function renderMembers() {
    const body = document.getElementById(
        "member-table-body"
    );

    body.replaceChildren();

    const query = document
        .getElementById("member-search")
        .value
        .trim()
        .toLowerCase();

    const filtered = members.filter(member => {
        if (!query) {
            return true;
        }

        return [
            member.memberCode,
            member.fullName,
            member.phone,
            member.email
        ].some(value =>
            String(value || "")
                .toLowerCase()
                .includes(query)
        );
    });

    if (filtered.length === 0) {
        appendEmptyRow(
            body,
            7,
            "Không tìm thấy hội viên"
        );
        return;
    }

    filtered.forEach(member => {
        const row = document.createElement("tr");

        appendCell(row, member.memberCode);

        const personCell =
            document.createElement("td");

        const name =
            document.createElement("strong");

        name.textContent = member.fullName;

        const dob =
            document.createElement("div");

        dob.className = "muted small";

        dob.textContent = member.dateOfBirth
            ? `Ngày sinh: ${formatDate(member.dateOfBirth)}`
            : "Chưa có ngày sinh";

        personCell.append(name, dob);
        row.appendChild(personCell);

        const contactCell =
            document.createElement("td");

        const phone =
            document.createElement("div");

        phone.textContent = member.phone;

        const email =
            document.createElement("div");

        email.className = "muted small";
        email.textContent =
            member.email || "Không có email";

        contactCell.append(phone, email);
        row.appendChild(contactCell);

        appendCell(
            row,
            branchName(member.homeBranchId)
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            createStatusBadge(member.status)
        );

        row.appendChild(statusCell);

        // F6: cột trạng thái tài khoản đăng nhập
        row.appendChild(accountCell(member));

        const actions =
            document.createElement("td");

        actions.className = "table-actions";

        const accountButton = actionButton(
            member.hasAccount
                ? "Đặt lại mật khẩu"
                : "Cấp tài khoản"
        );

        accountButton.addEventListener(
            "click",
            () => member.hasAccount
                ? resetMemberAccount(member)
                : issueMemberAccount(member)
        );

        const membershipButton =
            actionButton("Membership");

        membershipButton.addEventListener(
            "click",
            () => openMembership(member)
        );

        const editButton =
            actionButton("Sửa");

        editButton.addEventListener(
            "click",
            () => openEditMember(member)
        );

        actions.append(
            accountButton,
            membershipButton,
            editButton
        );

        row.appendChild(actions);
        body.appendChild(row);
    });
}

function openCreateMember() {
    const form = document.getElementById(
        "member-form"
    );

    form.reset();

    document.getElementById(
        "member-id"
    ).value = "";

    document.getElementById(
        "member-modal-title"
    ).textContent = "Thêm hội viên";

    document.getElementById(
        "member-status-field"
    ).classList.add("hidden");

    // Chỉ khi tạo mới mới cho chọn có cấp tài khoản hay không
    show("member-create-account-field");

    hide("member-form-error");
    show("member-modal");
}

function openEditMember(member) {
    document.getElementById(
        "member-modal-title"
    ).textContent = "Sửa hội viên";

    document.getElementById(
        "member-id"
    ).value = member.id;

    document.getElementById(
        "member-full-name"
    ).value = member.fullName;

    document.getElementById(
        "member-phone"
    ).value = member.phone;

    document.getElementById(
        "member-email"
    ).value = member.email || "";

    document.getElementById(
        "member-date-of-birth"
    ).value = member.dateOfBirth || "";

    document.getElementById(
        "member-home-branch"
    ).value = String(member.homeBranchId);

    document.getElementById(
        "member-status"
    ).value = member.status;

    document.getElementById(
        "member-status-field"
    ).classList.remove("hidden");

    // Sửa hội viên không đụng tới tài khoản (dùng nút Cấp/Đặt lại ở bảng)
    hide("member-create-account-field");

    hide("member-form-error");
    show("member-modal");
}

async function saveMember(event) {
    event.preventDefault();

    credentialsPayload = null;

    const id = document
        .getElementById("member-id")
        .value;

    const data = {
        fullName: document
            .getElementById("member-full-name")
            .value
            .trim(),

        phone: document
            .getElementById("member-phone")
            .value
            .trim(),

        email: nullable(
            document
                .getElementById("member-email")
                .value
        ),

        homeBranchId: Number(
            document
                .getElementById("member-home-branch")
                .value
        ),

        dateOfBirth: nullable(
            document
                .getElementById("member-date-of-birth")
                .value
        )
    };

    const button = document.getElementById(
        "member-save-button"
    );

    button.disabled = true;

    try {
        if (id) {
            await Api.put(
                `/api/v1/members/${id}`,
                {
                    ...data,
                    status: document
                        .getElementById("member-status")
                        .value
                }
            );

            showSuccess(
                "Đã cập nhật hội viên"
            );
        } else {
            const created = await Api.post(
                "/api/v1/members",
                {
                    ...data,

                    createAccount: document
                        .getElementById(
                            "member-create-account"
                        )
                        .checked
                }
            );

            if (created && created.account) {
                showSuccess(
                    "Đã tạo hội viên và cấp tài khoản"
                );
            } else {
                showSuccess(
                    "Đã tạo hội viên"
                );
            }

            credentialsPayload = created;
        }

        closeMemberModal();
        await loadMembers();

        if (credentialsPayload
            && credentialsPayload.account) {
            showCredentials(
                credentialsPayload.member,
                credentialsPayload.account
            );
        }
    } catch (error) {
        const box = document.getElementById(
            "member-form-error"
        );

        box.textContent =
            formatApiError(error);

        box.classList.remove("hidden");
    } finally {
        button.disabled = false;
    }
}

async function openMembership(member) {
    document.getElementById(
        "membership-modal-title"
    ).textContent =
        `Membership - ${member.fullName}`;

    document.getElementById(
        "current-membership"
    ).replaceChildren(
        createTextElement(
            "div",
            "Đang tải...",
            "empty-state"
        )
    );

    document.getElementById(
        "membership-history-body"
    ).replaceChildren();

    hide("membership-error");
    show("membership-modal");

    try {
        const history = await Api.get(
            `/api/v1/members/${member.id}/memberships`
        );

        renderMembershipHistory(history);

        try {
            const current = await Api.get(
                `/api/v1/members/${member.id}/memberships/current`
            );

            renderCurrentMembership(current);
        } catch (error) {
            if (error.status === 404) {
                renderNoCurrentMembership();
            } else {
                throw error;
            }
        }
    } catch (error) {
        showMembershipError(error.message);
    }
}

function renderCurrentMembership(membership) {
    const container =
        document.getElementById(
            "current-membership"
        );

    container.replaceChildren();

    const card = document.createElement("div");
    card.className = "membership-summary";

    const top = document.createElement("div");
    top.className = "membership-summary-top";

    const title = document.createElement("div");

    const strong =
        document.createElement("strong");

    strong.textContent =
        `Membership #${membership.id}`;

    const dates =
        document.createElement("div");

    dates.className = "muted";

    dates.textContent =
        `${formatDate(membership.startDate)} - `
        + `${formatDate(membership.endDate)}`;

    title.append(strong, dates);

    top.append(
        title,
        createStatusBadge(membership.status)
    );

    const services =
        document.createElement("div");

    services.className = "service-badges";

    membership.services.forEach(service => {
        const badge =
            document.createElement("span");

        badge.className = "badge badge-yellow";
        badge.textContent = service;

        services.appendChild(badge);
    });

    card.append(top, services);
    container.appendChild(card);
}

function renderNoCurrentMembership() {
    document.getElementById(
        "current-membership"
    ).replaceChildren(
        createTextElement(
            "div",
            "Hội viên chưa có membership đang hoạt động",
            "empty-state"
        )
    );
}

function renderMembershipHistory(history) {
    const body = document.getElementById(
        "membership-history-body"
    );

    body.replaceChildren();

    if (!history.length) {
        appendEmptyRow(
            body,
            6,
            "Chưa có lịch sử membership"
        );

        return;
    }

    history.forEach(item => {
        const row = document.createElement("tr");

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

        const serviceCell =
            document.createElement("td");

        const services =
            document.createElement("div");

        services.className = "service-badges";

        item.services.forEach(service => {
            const badge =
                document.createElement("span");

            badge.className =
                "badge badge-yellow";

            badge.textContent = service;

            services.appendChild(badge);
        });

        serviceCell.appendChild(services);
        row.appendChild(serviceCell);

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            createStatusBadge(item.status)
        );

        row.appendChild(statusCell);

        body.appendChild(row);
    });
}

function createStatusBadge(status) {
    const badge = document.createElement("span");
    badge.className = "badge";

    switch (status) {
        case "ACTIVE":
            badge.classList.add(
                "badge-success"
            );
            badge.textContent = "Hoạt động";
            break;

        case "EXPIRED":
            badge.classList.add(
                "badge-muted"
            );
            badge.textContent = "Hết hạn";
            break;

        case "REPLACED":
            badge.classList.add(
                "badge-warning"
            );
            badge.textContent = "Đã thay thế";
            break;

        case "CANCELLED":
            badge.classList.add(
                "badge-danger"
            );
            badge.textContent = "Đã hủy";
            break;

        case "INACTIVE":
            badge.classList.add(
                "badge-muted"
            );
            badge.textContent = "Ngừng hoạt động";
            break;

        default:
            badge.classList.add(
                "badge-muted"
            );
            badge.textContent = status;
    }

    return badge;
}

function branchName(branchId) {
    const branch = branches.find(
        item => item.id === branchId
    );

    return branch
        ? branch.name
        : `#${branchId}`;
}

function actionButton(text) {
    const button = document.createElement("button");

    button.type = "button";
    button.className =
        "button button-small button-secondary";

    button.textContent = text;

    return button;
}

function appendCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value ?? "—";
    row.appendChild(cell);
}

function appendEmptyRow(
    body,
    colspan,
    message
) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");

    cell.colSpan = colspan;
    cell.className = "empty-cell";
    cell.textContent = message;

    row.appendChild(cell);
    body.appendChild(row);
}

function createTextElement(
    tag,
    text,
    className
) {
    const element =
        document.createElement(tag);

    element.textContent = text;

    if (className) {
        element.className = className;
    }

    return element;
}

function closeMemberModal() {
    hide("member-modal");
}

function closeMembershipModal() {
    hide("membership-modal");
}

function show(id) {
    document
        .getElementById(id)
        .classList
        .remove("hidden");
}

function hide(id) {
    document
        .getElementById(id)
        .classList
        .add("hidden");
}

function nullable(value) {
    const normalized =
        String(value || "").trim();

    return normalized || null;
}

function formatDate(value) {
    if (!value) {
        return "—";
    }

    const parts = value.split("-");

    if (parts.length !== 3) {
        return value;
    }

    return `${parts[2]}/${parts[1]}/${parts[0]}`;
}

function showError(message) {
    const box = document.getElementById(
        "page-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function clearPageError() {
    hide("page-error");
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

function showMembershipError(message) {
    const box = document.getElementById(
        "membership-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function formatApiError(error) {
    if (error.errors
        && Object.keys(error.errors).length) {

        return Object.values(error.errors)
            .join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}

// =====================================================================
// F6 – cột "Tài khoản" + cấp / đặt lại mật khẩu + phiếu Copy / In
// =====================================================================

function accountCell(member) {
    const cell = document.createElement("td");

    const badge = document.createElement("span");

    badge.className = member.hasAccount
        ? "badge badge-success"
        : "badge badge-muted";

    badge.textContent = member.hasAccount
        ? "Đã có"
        : "Chưa có";

    cell.appendChild(badge);

    if (member.hasAccount && member.accountUsername) {
        const username =
            document.createElement("div");

        username.className = "muted small";
        username.textContent =
            member.accountUsername;

        cell.appendChild(username);
    }

    return cell;
}

async function issueMemberAccount(member) {
    await requestAccountAction(
        member,
        `/api/v1/members/${member.id}/account`,
        "Đã cấp tài khoản đăng nhập cho hội viên"
    );
}

async function resetMemberAccount(member) {
    const confirmed = window.confirm(
        `Đặt lại mật khẩu của ${member.fullName}? `
        + "Mật khẩu tạm cũ sẽ không dùng được nữa."
    );

    if (!confirmed) {
        return;
    }

    await requestAccountAction(
        member,
        `/api/v1/members/${member.id}/account/reset-password`,
        "Đã đặt lại mật khẩu cho hội viên"
    );
}

async function requestAccountAction(
    member,
    url,
    successMessage
) {
    clearPageError();

    try {
        const account = await Api.post(url);

        showSuccess(successMessage);

        await loadMembers();

        showCredentials(member, account);
    } catch (error) {
        showError(formatApiError(error));
    }
}

function showCredentials(member, account) {
    credentialsPassword =
        account.temporaryPassword;

    setText(
        "credentials-name",
        member.fullName || "—"
    );

    setText(
        "credentials-member-code",
        member.memberCode || "—"
    );

    setText(
        "credentials-branch",
        member.homeBranchId
            ? branchName(member.homeBranchId)
            : "—"
    );

    setText(
        "credentials-login-url",
        `${window.location.origin}/login`
    );

    setText(
        "credentials-username",
        account.username || "—"
    );

    setText(
        "credentials-password",
        credentialsPassword || "—"
    );

    hide("credentials-error");
    show("credentials-modal");
}

function closeCredentials() {
    credentialsPassword = null;

    setText("credentials-password", "—");

    hide("credentials-modal");
}

async function copyCredentials() {
    if (!credentialsPassword) {
        return;
    }

    const text = [
        "GYMFIT - Thong tin dang nhap",
        `Ho ten: ${textOf("credentials-name")}`,
        `Ma hoi vien: ${textOf("credentials-member-code")}`,
        `Chi nhanh: ${textOf("credentials-branch")}`,
        `Dang nhap tai: ${textOf("credentials-login-url")}`,
        `Ten dang nhap: ${textOf("credentials-username")}`,
        `Mat khau tam: ${credentialsPassword}`,
        "Luu y: doi mat khau sau lan dang nhap dau tien."
    ].join("\n");

    try {
        if (navigator.clipboard) {
            await navigator.clipboard.writeText(text);
        } else {
            legacyCopy(text);
        }

        flashCopied();
    } catch (error) {
        showCredentialsError(
            "Không sao chép được: "
            + (error.message || "lỗi không rõ")
            + ". Hãy bôi chọn và copy thủ công."
        );
    }
}

function printCredentials() {
    window.print();
}

function legacyCopy(text) {
    const area = document.createElement("textarea");

    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";

    document.body.appendChild(area);
    area.select();

    const copied = document.execCommand("copy");

    document.body.removeChild(area);

    if (!copied) {
        throw new Error("trình duyệt không hỗ trợ");
    }
}

function flashCopied() {
    const button =
        document.getElementById("credentials-copy");

    const original = button.textContent;

    button.textContent = "Đã sao chép";

    setTimeout(
        () => {
            button.textContent = original;
        },
        2000
    );
}

function showCredentialsError(message) {
    const box = document.getElementById(
        "credentials-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function setText(id, value) {
    document.getElementById(id)
        .textContent = value;
}

function textOf(id) {
    return document.getElementById(id)
        .textContent;
}