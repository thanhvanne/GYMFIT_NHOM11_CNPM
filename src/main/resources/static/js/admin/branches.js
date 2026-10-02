let branches = [];
let selectedBranchId = null;

const serviceDefinitions = [
    {
        code: "GYM",
        label: "Gym"
    },
    {
        code: "BOXING",
        label: "Boxing"
    },
    {
        code: "PICKLEBALL",
        label: "Pickleball"
    }
];

const dayDefinitions = [
    { value: 1, label: "Thứ 2" },
    { value: 2, label: "Thứ 3" },
    { value: 3, label: "Thứ 4" },
    { value: 4, label: "Thứ 5" },
    { value: 5, label: "Thứ 6" },
    { value: 6, label: "Thứ 7" },
    { value: 7, label: "Chủ nhật" }
];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();
        buildServiceConfig();
        buildOperatingHours();

        await loadBranches();
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
        .getElementById("create-branch-button")
        .addEventListener(
            "click",
            openCreateBranch
        );

    document
        .getElementById("branch-modal-close")
        .addEventListener(
            "click",
            closeBranchModal
        );

    document
        .getElementById("branch-cancel-button")
        .addEventListener(
            "click",
            closeBranchModal
        );

    document
        .getElementById("branch-form")
        .addEventListener(
            "submit",
            saveBranch
        );

    document
        .getElementById("branch-search")
        .addEventListener(
            "input",
            renderBranches
        );

    document
        .getElementById("branch-status-filter")
        .addEventListener(
            "change",
            loadBranches
        );

    document
        .getElementById("config-close")
        .addEventListener(
            "click",
            closeConfigModal
        );

    document
        .getElementById("save-services-button")
        .addEventListener(
            "click",
            saveServices
        );

    document
        .getElementById("save-hours-button")
        .addEventListener(
            "click",
            saveHours
        );
}

async function loadBranches() {
    clearMessage();

    const status = document
        .getElementById("branch-status-filter")
        .value;

    try {
        const url = status
            ? `/api/v1/branches?status=${encodeURIComponent(status)}`
            : "/api/v1/branches";

        branches = await Api.get(url);

        renderBranches();
    } catch (error) {
        showError(error.message);
    }
}

function renderBranches() {
    const tbody = document.getElementById(
        "branch-table-body"
    );

    tbody.replaceChildren();

    const query = document
        .getElementById("branch-search")
        .value
        .trim()
        .toLowerCase();

    const filtered = branches.filter(branch => {
        if (!query) {
            return true;
        }

        return [
            branch.code,
            branch.name,
            branch.address,
            branch.phone
        ].some(value =>
            String(value || "")
                .toLowerCase()
                .includes(query)
        );
    });

    if (filtered.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 6;
        cell.className = "empty-cell";
        cell.textContent = "Không có chi nhánh";

        row.appendChild(cell);
        tbody.appendChild(row);

        return;
    }

    filtered.forEach(branch => {
        const row = document.createElement("tr");

        appendCell(row, branch.code);

        const nameCell =
            document.createElement("td");

        const name = document.createElement("strong");
        name.textContent = branch.name;

        nameCell.appendChild(name);
        row.appendChild(nameCell);

        appendCell(row, branch.address);
        appendCell(row, branch.phone || "—");

        const statusCell =
            document.createElement("td");

        const badge =
            document.createElement("span");

        badge.className =
            branch.status === "ACTIVE"
                ? "badge badge-success"
                : "badge badge-muted";

        badge.textContent =
            branch.status === "ACTIVE"
                ? "Hoạt động"
                : "Ngừng hoạt động";

        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className = "table-actions";

        const editButton =
            actionButton("Sửa");

        editButton.addEventListener(
            "click",
            () => openEditBranch(branch)
        );

        const configButton =
            actionButton("Cấu hình");

        configButton.addEventListener(
            "click",
            () => openConfig(branch)
        );

        actions.append(
            editButton,
            configButton
        );

        row.appendChild(actions);
        tbody.appendChild(row);
    });
}

function appendCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value ?? "—";
    row.appendChild(cell);
}

function actionButton(text) {
    const button = document.createElement("button");

    button.type = "button";
    button.className = "button button-small button-secondary";
    button.textContent = text;

    return button;
}

function openCreateBranch() {
    document.getElementById(
        "branch-modal-title"
    ).textContent = "Thêm chi nhánh";

    document.getElementById(
        "branch-form"
    ).reset();

    document.getElementById(
        "branch-id"
    ).value = "";

    document.getElementById(
        "branch-timezone"
    ).value = "Asia/Ho_Chi_Minh";

    document.getElementById(
        "branch-status"
    ).value = "ACTIVE";

    document.getElementById(
        "branch-status-field"
    ).classList.add("hidden");

    hideElement("branch-form-error");
    showElement("branch-modal");
}

function openEditBranch(branch) {
    document.getElementById(
        "branch-modal-title"
    ).textContent = "Sửa chi nhánh";

    document.getElementById(
        "branch-id"
    ).value = branch.id;

    document.getElementById(
        "branch-code"
    ).value = branch.code;

    document.getElementById(
        "branch-name"
    ).value = branch.name;

    document.getElementById(
        "branch-address"
    ).value = branch.address;

    document.getElementById(
        "branch-phone"
    ).value = branch.phone || "";

    document.getElementById(
        "branch-timezone"
    ).value = branch.timezone;

    document.getElementById(
        "branch-status"
    ).value = branch.status;

    document.getElementById(
        "branch-status-field"
    ).classList.remove("hidden");

    hideElement("branch-form-error");
    showElement("branch-modal");
}

async function saveBranch(event) {
    event.preventDefault();

    const id = document
        .getElementById("branch-id")
        .value;

    const common = {
        code: document
            .getElementById("branch-code")
            .value
            .trim(),

        name: document
            .getElementById("branch-name")
            .value
            .trim(),

        address: document
            .getElementById("branch-address")
            .value
            .trim(),

        phone: nullableValue(
            document
                .getElementById("branch-phone")
                .value
        ),

        timezone: document
            .getElementById("branch-timezone")
            .value
            .trim()
    };

    const saveButton =
        document.getElementById(
            "branch-save-button"
        );

    saveButton.disabled = true;

    try {
        if (id) {
            await Api.put(
                `/api/v1/branches/${id}`,
                {
                    ...common,
                    status: document
                        .getElementById("branch-status")
                        .value
                }
            );

            showSuccess("Đã cập nhật chi nhánh");
        } else {
            await Api.post(
                "/api/v1/branches",
                common
            );

            showSuccess("Đã tạo chi nhánh");
        }

        closeBranchModal();
        await loadBranches();
    } catch (error) {
        const box = document.getElementById(
            "branch-form-error"
        );

        box.textContent =
            formatApiError(error);

        box.classList.remove("hidden");
    } finally {
        saveButton.disabled = false;
    }
}

function closeBranchModal() {
    hideElement("branch-modal");
}

function buildServiceConfig() {
    const container = document.getElementById(
        "service-config-list"
    );

    container.replaceChildren();

    serviceDefinitions.forEach(service => {
        const row = document.createElement("div");
        row.className = "service-config-row";
        row.dataset.service = service.code;

        const enabledLabel =
            document.createElement("label");

        enabledLabel.className = "checkbox-field";

        const enabled =
            document.createElement("input");

        enabled.type = "checkbox";
        enabled.className = "service-enabled";

        const title =
            document.createElement("strong");

        title.textContent = service.label;

        enabledLabel.append(enabled, title);

        const duration =
            document.createElement("input");

        duration.type = "number";
        duration.min = "30";
        duration.max = "120";
        duration.value = "60";
        duration.className = "service-duration";
        duration.title = "Thời lượng (phút)";

        const capacity =
            document.createElement("input");

        capacity.type = "number";
        capacity.min = "1";
        capacity.value = "1";
        capacity.className = "service-capacity";
        capacity.title = "Sức chứa";

        const bookingLabel =
            document.createElement("label");

        bookingLabel.className = "checkbox-field";

        const bookingEnabled =
            document.createElement("input");

        bookingEnabled.type = "checkbox";
        bookingEnabled.checked = true;
        bookingEnabled.className =
            "service-booking-enabled";

        bookingLabel.append(
            bookingEnabled,
            document.createTextNode(" Cho đặt lịch")
        );

        row.append(
            enabledLabel,
            duration,
            capacity,
            bookingLabel
        );

        container.appendChild(row);
    });
}

function buildOperatingHours() {
    const container = document.getElementById(
        "operating-hours-list"
    );
    container.replaceChildren();

    dayDefinitions.forEach(day => {
        const row = document.createElement("div");
        row.className = "hours-row";
        row.dataset.day = String(day.value);

        const enabledLabel =
            document.createElement("label");
        enabledLabel.className = "checkbox-field";

        const enabled =
            document.createElement("input");
        enabled.type = "checkbox";
        enabled.className = "day-enabled";

        const dayName =
            document.createElement("strong");
        dayName.textContent = day.label;

        enabledLabel.append(enabled, dayName);

        const open =
            document.createElement("input");
        open.type = "time";
        open.className = "day-open";
        open.value = "05:00";

        const close =
            document.createElement("input");
        close.type = "time";
        close.className = "day-close";
        close.value = "23:00";

        row.append(
            enabledLabel,
            open,
            close
        );

        container.appendChild(row);
    });
}

async function openConfig(branch) {
    selectedBranchId = branch.id;

    document.getElementById(
        "config-title"
    ).textContent =
        `Cấu hình - ${branch.name}`;

    hideElement("config-error");
    showElement("config-modal");

    resetConfigInputs();

    try {
        const [services, hours] =
            await Promise.all([
                Api.get(
                    `/api/v1/branches/${branch.id}/services`
                ),
                Api.get(
                    `/api/v1/branches/${branch.id}/operating-hours`
                )
            ]);

        applyServices(services);
        applyHours(hours);
    } catch (error) {
        showConfigError(error.message);
    }
}

function resetConfigInputs() {
    document
        .querySelectorAll(".service-config-row")
        .forEach(row => {
            row.querySelector(
                ".service-enabled"
            ).checked = false;

            row.querySelector(
                ".service-duration"
            ).value = "60";

            row.querySelector(
                ".service-capacity"
            ).value = "1";

            row.querySelector(
                ".service-booking-enabled"
            ).checked = true;
        });

    document
        .querySelectorAll(".hours-row")
        .forEach(row => {
            row.querySelector(
                ".day-enabled"
            ).checked = false;
        });
}

function applyServices(services) {
    services.forEach(service => {
        const row = document.querySelector(
            `.service-config-row[data-service="${service.serviceCode}"]`
        );

        if (!row) {
            return;
        }

        row.querySelector(
            ".service-enabled"
        ).checked = true;

        row.querySelector(
            ".service-duration"
        ).value = service.bookingDurationMinutes;

        row.querySelector(
            ".service-capacity"
        ).value = service.capacity;

        row.querySelector(
            ".service-booking-enabled"
        ).checked = service.bookingEnabled;
    });
}

function applyHours(hours) {
    hours.forEach(hour => {
        const row = document.querySelector(
            `.hours-row[data-day="${hour.dayOfWeek}"]`
        );

        if (!row) {
            return;
        }

        row.querySelector(
            ".day-enabled"
        ).checked = true;

        row.querySelector(
            ".day-open"
        ).value = normalizeTime(hour.openTime);

        row.querySelector(
            ".day-close"
        ).value = normalizeTime(hour.closeTime);
    });
}

async function saveServices() {
    if (!selectedBranchId) {
        return;
    }

    const services = [];

    document
        .querySelectorAll(".service-config-row")
        .forEach(row => {
            if (!row.querySelector(
                ".service-enabled"
            ).checked) {
                return;
            }

            services.push({
                serviceCode: row.dataset.service,

                bookingDurationMinutes:
                    Number(
                        row.querySelector(
                            ".service-duration"
                        ).value
                    ),

                capacity:
                    Number(
                        row.querySelector(
                            ".service-capacity"
                        ).value
                    ),

                bookingEnabled:
                row.querySelector(
                    ".service-booking-enabled"
                ).checked
            });
        });

    if (services.length === 0) {
        showConfigError(
            "Chi nhánh phải có ít nhất một dịch vụ"
        );
        return;
    }

    try {
        await Api.put(
            `/api/v1/branches/${selectedBranchId}/services`,
            { services }
        );

        showSuccess("Đã lưu cấu hình dịch vụ");
        hideElement("config-error");
    } catch (error) {
        showConfigError(
            formatApiError(error)
        );
    }
}

async function saveHours() {
    if (!selectedBranchId) {
        return;
    }

    const hours = [];
    let validationError = null;

    document
        .querySelectorAll(".hours-row")
        .forEach(row => {
            if (validationError) {
                return;
            }

            const enabled =
                row.querySelector(
                    ".day-enabled"
                ).checked;

            if (!enabled) {
                return;
            }

            const openTime =
                row.querySelector(
                    ".day-open"
                ).value;

            const closeTime =
                row.querySelector(
                    ".day-close"
                ).value;

            if (!openTime || !closeTime) {
                validationError =
                    "Vui lòng nhập đầy đủ giờ mở và đóng cửa";
                return;
            }

            if (openTime >= closeTime) {
                validationError =
                    "Giờ mở cửa phải trước giờ đóng cửa";
                return;
            }

            hours.push({
                dayOfWeek:
                    Number(
                        row.dataset.day
                    ),
                openTime,
                closeTime
            });
        });

    if (validationError) {
        showConfigError(
            validationError
        );
        return;
    }

    if (hours.length === 0) {
        showConfigError(
            "Phải cấu hình ít nhất một ngày hoạt động"
        );
        return;
    }

    try {
        await Api.put(
            `/api/v1/branches/${selectedBranchId}/operating-hours`,
            {
                hours
            }
        );

        hideElement(
            "config-error"
        );

        showSuccess(
            "Đã lưu giờ hoạt động"
        );
    } catch (error) {
        showConfigError(
            formatApiError(error)
        );
    }
}

function closeConfigModal() {
    selectedBranchId = null;
    hideElement("config-modal");
}

function normalizeTime(value) {
    if (!value) {
        return "";
    }

    return value.substring(0, 5);
}

function nullableValue(value) {
    const normalized = value.trim();

    return normalized
        ? normalized
        : null;
}

function showElement(id) {
    document
        .getElementById(id)
        .classList
        .remove("hidden");
}

function hideElement(id) {
    document
        .getElementById(id)
        .classList
        .add("hidden");
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

    window.setTimeout(
        () => box.classList.add("hidden"),
        3000
    );
}

function clearMessage() {
    hideElement("page-error");
}

function showConfigError(message) {
    const box = document.getElementById(
        "config-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function formatApiError(error) {
    if (error.errors
        && Object.keys(error.errors).length > 0) {

        return Object.values(error.errors)
            .join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}