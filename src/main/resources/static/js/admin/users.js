let users = [];
let branches = [];
let members = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const currentUser =
            Auth.requireRole("ADMIN");

        if (!currentUser) {
            return;
        }

        bindEvents();

        try {
            await Promise.all([
                loadBranches(),
                loadMembers()
            ]);

            await loadUsers();
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
        "create-user-button"
    ).addEventListener(
        "click",
        openCreateUser
    );

    document.getElementById(
        "user-modal-close"
    ).addEventListener(
        "click",
        closeUserModal
    );

    document.getElementById(
        "user-cancel-button"
    ).addEventListener(
        "click",
        closeUserModal
    );

    document.getElementById(
        "user-form"
    ).addEventListener(
        "submit",
        saveUser
    );

    document.getElementById(
        "user-role"
    ).addEventListener(
        "change",
        updateScopeFields
    );

    document.getElementById(
        "user-search"
    ).addEventListener(
        "input",
        renderUsers
    );

    document.getElementById(
        "user-role-filter"
    ).addEventListener(
        "change",
        loadUsers
    );
}

async function loadBranches() {
    branches = await Api.get(
        "/api/v1/branches"
    );

    const select = document.getElementById(
        "user-branch"
    );

    select.replaceChildren();

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent =
        "Chọn chi nhánh";

    select.appendChild(placeholder);

    branches
        .filter(
            branch =>
                branch.status === "ACTIVE"
        )
        .forEach(branch => {
            const option =
                document.createElement("option");

            option.value = branch.id;
            option.textContent = branch.name;

            select.appendChild(option);
        });
}

async function loadMembers() {
    members = await Api.get(
        "/api/v1/members?status=ACTIVE"
    );

    renderMemberOptions();
}

function renderMemberOptions() {
    const select = document.getElementById(
        "user-member"
    );

    select.replaceChildren();

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent =
        "Chọn hội viên";

    select.appendChild(placeholder);

    members.forEach(member => {
        const option =
            document.createElement("option");

        option.value = member.id;

        option.textContent =
            `${member.memberCode} - `
            + member.fullName;

        select.appendChild(option);
    });
}

async function loadUsers() {
    const role = document.getElementById(
        "user-role-filter"
    ).value;

    try {
        users = await Api.get(
            role
                ? `/api/v1/users?role=${role}`
                : "/api/v1/users"
        );

        renderUsers();
    } catch (error) {
        showPageError(
            formatApiError(error)
        );
    }
}

function renderUsers() {
    const body = document.getElementById(
        "user-table-body"
    );

    body.replaceChildren();

    const search = document.getElementById(
        "user-search"
    ).value.trim().toLowerCase();

    const filtered = users.filter(user =>
        !search
        || user.fullName
            .toLowerCase()
            .includes(search)
        || user.email
            .toLowerCase()
            .includes(search)
    );

    if (!filtered.length) {
        appendEmptyRow(
            body,
            7,
            "Không có người dùng"
        );
        return;
    }

    filtered.forEach(user => {
        const row =
            document.createElement("tr");

        const userCell =
            document.createElement("td");

        const wrapper =
            document.createElement("div");

        wrapper.className =
            "user-table-person";

        const avatar =
            document.createElement("div");

        avatar.className =
            "avatar avatar-small";

        avatar.textContent =
            initial(user.fullName);

        const name =
            document.createElement("strong");

        name.textContent =
            user.fullName;

        wrapper.append(avatar, name);

        userCell.appendChild(wrapper);
        row.appendChild(userCell);

        appendCell(row, user.email);

        const roleCell =
            document.createElement("td");

        const roleBadge =
            document.createElement("span");

        roleBadge.className =
            "badge badge-role";

        roleBadge.textContent =
            roleText(user.role);

        roleCell.appendChild(roleBadge);
        row.appendChild(roleCell);

        appendCell(
            row,
            scopeText(user)
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            userStatusBadge(user.status)
        );

        row.appendChild(statusCell);

        appendCell(
            row,
            formatDateTime(
                user.createdAtUtc
            )
        );

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        const edit =
            actionButton("Sửa");

        edit.addEventListener(
            "click",
            () => openEditUser(user)
        );

        actions.appendChild(edit);
        row.appendChild(actions);

        body.appendChild(row);
    });
}

function openCreateUser() {
    document.getElementById(
        "user-form"
    ).reset();

    document.getElementById(
        "user-id"
    ).value = "";

    document.getElementById(
        "user-modal-title"
    ).textContent =
        "Thêm người dùng";

    document.getElementById(
        "password-label"
    ).textContent =
        "Mật khẩu";

    const password = document.getElementById(
        "user-password"
    );

    password.required = true;
    password.minLength = 6;

    document.getElementById(
        "user-role"
    ).value = "ADMIN";

    document.getElementById(
        "user-status"
    ).value = "ACTIVE";

    updateScopeFields();

    hide("user-form-error");
    show("user-modal");
}

function openEditUser(user) {
    document.getElementById(
        "user-id"
    ).value = user.id;

    document.getElementById(
        "user-modal-title"
    ).textContent =
        "Sửa người dùng";

    document.getElementById(
        "user-full-name"
    ).value = user.fullName;

    document.getElementById(
        "user-email"
    ).value = user.email;

    document.getElementById(
        "user-role"
    ).value = user.role;

    document.getElementById(
        "user-status"
    ).value = user.status;

    const password = document.getElementById(
        "user-password"
    );

    password.value = "";
    password.required = false;
    password.minLength = 6;

    document.getElementById(
        "password-label"
    ).textContent =
        "Mật khẩu mới (để trống nếu giữ nguyên)";

    updateScopeFields();

    if (user.branchId != null) {
        ensureBranchOption(user.branchId);

        document.getElementById(
            "user-branch"
        ).value =
            String(user.branchId);
    }

    if (user.memberId != null) {
        ensureMemberOption(user.memberId);

        document.getElementById(
            "user-member"
        ).value =
            String(user.memberId);
    }

    hide("user-form-error");
    show("user-modal");
}

function updateScopeFields() {
    const role = document.getElementById(
        "user-role"
    ).value;

    const branchField =
        document.getElementById(
            "user-branch-field"
        );

    const memberField =
        document.getElementById(
            "user-member-field"
        );

    const branch =
        document.getElementById(
            "user-branch"
        );

    const member =
        document.getElementById(
            "user-member"
        );

    branch.required = false;
    member.required = false;

    branchField.classList.add("hidden");
    memberField.classList.add("hidden");

    if (role === "BRANCH_MANAGER") {
        branchField.classList.remove(
            "hidden"
        );

        branch.required = true;
    }

    if (role === "MEMBER") {
        memberField.classList.remove(
            "hidden"
        );

        member.required = true;
    }
}

async function saveUser(event) {
    event.preventDefault();

    const id = document.getElementById(
        "user-id"
    ).value;

    const role = document.getElementById(
        "user-role"
    ).value;

    const password =
        document.getElementById(
            "user-password"
        ).value;

    const data = {
        fullName:
            document.getElementById(
                "user-full-name"
            ).value.trim(),

        email:
            document.getElementById(
                "user-email"
            ).value.trim(),

        password:
            id
                ? nullable(password)
                : password,

        role,

        status:
        document.getElementById(
            "user-status"
        ).value,

        branchId:
            role === "BRANCH_MANAGER"
                ? selectedNumber(
                    "user-branch"
                )
                : null,

        memberId:
            role === "MEMBER"
                ? selectedNumber(
                    "user-member"
                )
                : null
    };

    const button = document.getElementById(
        "user-save-button"
    );

    button.disabled = true;

    try {
        if (id) {
            await Api.put(
                `/api/v1/users/${id}`,
                data
            );

            showSuccess(
                "Đã cập nhật người dùng"
            );
        } else {
            await Api.post(
                "/api/v1/users",
                data
            );

            showSuccess(
                "Đã tạo người dùng"
            );
        }

        closeUserModal();

        await loadUsers();
    } catch (error) {
        showUserFormError(
            formatApiError(error)
        );
    } finally {
        button.disabled = false;
    }
}

function ensureBranchOption(id) {
    const select = document.getElementById(
        "user-branch"
    );

    const exists = Array
        .from(select.options)
        .some(
            option =>
                Number(option.value) === id
        );

    if (exists) {
        return;
    }

    const branch = branches.find(
        value => value.id === id
    );

    if (!branch) {
        return;
    }

    const option =
        document.createElement("option");

    option.value = branch.id;

    option.textContent =
        `${branch.name} (Ngừng hoạt động)`;

    select.appendChild(option);
}

function ensureMemberOption(id) {
    const select = document.getElementById(
        "user-member"
    );

    const exists = Array
        .from(select.options)
        .some(
            option =>
                Number(option.value) === id
        );

    if (exists) {
        return;
    }

    const option =
        document.createElement("option");

    option.value = id;
    option.textContent =
        `Hội viên #${id}`;

    select.appendChild(option);
}

function scopeText(user) {
    if (user.role === "ADMIN") {
        return "Toàn hệ thống";
    }

    if (user.role === "BRANCH_MANAGER") {
        const branch = branches.find(
            item =>
                item.id === user.branchId
        );

        return branch
            ? branch.name
            : `Branch #${user.branchId}`;
    }

    const member = members.find(
        item =>
            item.id === user.memberId
    );

    return member
        ? `${member.memberCode} - ${member.fullName}`
        : `Member #${user.memberId}`;
}

function roleText(role) {
    if (role === "BRANCH_MANAGER") {
        return "MANAGER";
    }

    return role;
}

function userStatusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "ACTIVE") {
        badge.classList.add(
            "badge-success"
        );

        badge.textContent = "Hoạt động";
    } else if (status === "LOCKED") {
        badge.classList.add(
            "badge-warning"
        );

        badge.textContent = "Đã khóa";
    } else {
        badge.classList.add(
            "badge-danger"
        );

        badge.textContent = "Vô hiệu hóa";
    }

    return badge;
}

function selectedNumber(id) {
    const value =
        document.getElementById(id).value;

    return value
        ? Number(value)
        : null;
}

function nullable(value) {
    const result =
        String(value || "").trim();

    return result || null;
}

function initial(name) {
    return String(name || "?")
        .trim()
        .charAt(0)
        .toUpperCase();
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

function closeUserModal() {
    hide("user-modal");
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
    const box = document.getElementById(
        "page-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function showUserFormError(message) {
    const box = document.getElementById(
        "user-form-error"
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

    return error.message
        || "Có lỗi xảy ra";
}