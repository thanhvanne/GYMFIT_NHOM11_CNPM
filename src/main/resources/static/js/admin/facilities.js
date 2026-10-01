let branches = [];
let facilities = [];
let supportedServices = [];

const facilityTypeByService = {
    GYM: "GYM_AREA",
    BOXING: "BOXING_ROOM",
    PICKLEBALL: "PICKLEBALL_COURT"
};

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        await loadBranches();
        await loadFacilities();
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
        .getElementById("create-facility-button")
        .addEventListener(
            "click",
            openCreateFacility
        );

    document
        .getElementById("facility-modal-close")
        .addEventListener(
            "click",
            closeFacilityModal
        );

    document
        .getElementById("facility-cancel-button")
        .addEventListener(
            "click",
            closeFacilityModal
        );

    document
        .getElementById("facility-form")
        .addEventListener(
            "submit",
            saveFacility
        );

    document
        .getElementById("facility-branch-filter")
        .addEventListener(
            "change",
            loadFacilities
        );

    document
        .getElementById("facility-service-filter")
        .addEventListener(
            "change",
            renderFacilities
        );

    document
        .getElementById("facility-status-filter")
        .addEventListener(
            "change",
            renderFacilities
        );

    document
        .getElementById("facility-branch")
        .addEventListener(
            "change",
            async event => {
                await loadBranchServices(
                    Number(event.target.value)
                );
            }
        );

    document
        .getElementById("facility-service")
        .addEventListener(
            "change",
            updateFacilityType
        );
}

async function loadBranches() {
    branches = await Api.get(
        "/api/v1/branches"
    );

    const filter = document.getElementById(
        "facility-branch-filter"
    );

    const form = document.getElementById(
        "facility-branch"
    );

    filter.replaceChildren();
    form.replaceChildren();

    const all = document.createElement("option");
    all.value = "";
    all.textContent = "Tất cả chi nhánh";
    filter.appendChild(all);

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent = "Chọn chi nhánh";

    form.appendChild(placeholder);

    branches.forEach(branch => {
        const option =
            document.createElement("option");

        option.value = branch.id;
        option.textContent = branch.name;

        filter.appendChild(option.cloneNode(true));

        if (branch.status === "ACTIVE") {
            form.appendChild(option);
        }
    });
}

async function loadFacilities() {
    try {
        const branchId = document
            .getElementById("facility-branch-filter")
            .value;

        facilities = await Api.get(
            branchId
                ? `/api/v1/facilities?branchId=${branchId}`
                : "/api/v1/facilities"
        );

        renderFacilities();
    } catch (error) {
        showPageError(error.message);
    }
}

function renderFacilities() {
    const body = document.getElementById(
        "facility-table-body"
    );

    body.replaceChildren();

    const service = document
        .getElementById("facility-service-filter")
        .value;

    const status = document
        .getElementById("facility-status-filter")
        .value;

    const filtered = facilities.filter(item =>
        (!service || item.serviceCode === service)
        && (!status || item.status === status)
    );

    if (!filtered.length) {
        appendEmptyRow(
            body,
            7,
            "Không có cơ sở vật chất"
        );
        return;
    }

    filtered.forEach(facility => {
        const row = document.createElement("tr");

        appendCell(row, facility.name);
        appendCell(
            row,
            branchName(facility.branchId)
        );
        appendCell(row, facility.serviceCode);
        appendCell(
            row,
            facilityTypeName(
                facility.facilityType
            )
        );
        appendCell(row, facility.capacity);

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            facilityStatusBadge(
                facility.status
            )
        );

        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className = "table-actions";

        const edit = actionButton("Sửa");

        edit.addEventListener(
            "click",
            () => openEditFacility(facility)
        );

        actions.appendChild(edit);
        row.appendChild(actions);

        body.appendChild(row);
    });
}

async function openCreateFacility() {
    document.getElementById(
        "facility-form"
    ).reset();

    document.getElementById(
        "facility-id"
    ).value = "";

    document.getElementById(
        "facility-modal-title"
    ).textContent =
        "Thêm cơ sở vật chất";

    document.getElementById(
        "facility-branch"
    ).disabled = false;

    document.getElementById(
        "facility-service"
    ).replaceChildren();

    document.getElementById(
        "facility-type"
    ).replaceChildren();

    hide("facility-form-error");
    show("facility-modal");
}

async function openEditFacility(facility) {
    document.getElementById(
        "facility-id"
    ).value = facility.id;

    document.getElementById(
        "facility-modal-title"
    ).textContent =
        "Sửa cơ sở vật chất";

    const branchSelect =
        document.getElementById(
            "facility-branch"
        );

    ensureBranchOption(
        branchSelect,
        facility.branchId
    );

    branchSelect.value =
        String(facility.branchId);

    branchSelect.disabled = true;

    await loadBranchServices(
        facility.branchId
    );

    document.getElementById(
        "facility-service"
    ).value = facility.serviceCode;

    updateFacilityType();

    document.getElementById(
        "facility-name"
    ).value = facility.name;

    document.getElementById(
        "facility-capacity"
    ).value = facility.capacity;

    document.getElementById(
        "facility-status"
    ).value = facility.status;

    hide("facility-form-error");
    show("facility-modal");
}

async function loadBranchServices(branchId) {
    const select = document.getElementById(
        "facility-service"
    );

    select.replaceChildren();

    if (!branchId) {
        return;
    }

    supportedServices = await Api.get(
        `/api/v1/branches/${branchId}/services`
    );

    supportedServices.forEach(config => {
        const option =
            document.createElement("option");

        option.value = config.serviceCode;
        option.textContent =
            config.serviceCode;

        select.appendChild(option);
    });

    updateFacilityType();
}

function updateFacilityType() {
    const service = document.getElementById(
        "facility-service"
    ).value;

    const select = document.getElementById(
        "facility-type"
    );

    select.replaceChildren();

    if (!service) {
        return;
    }

    const type =
        facilityTypeByService[service];

    const option =
        document.createElement("option");

    option.value = type;
    option.textContent =
        facilityTypeName(type);

    select.appendChild(option);
}

async function saveFacility(event) {
    event.preventDefault();

    const id = document.getElementById(
        "facility-id"
    ).value;

    const branchId = Number(
        document.getElementById(
            "facility-branch"
        ).value
    );

    const data = {
        serviceCode:
        document.getElementById(
            "facility-service"
        ).value,

        facilityType:
        document.getElementById(
            "facility-type"
        ).value,

        name:
            document.getElementById(
                "facility-name"
            ).value.trim(),

        capacity: Number(
            document.getElementById(
                "facility-capacity"
            ).value
        ),

        status:
        document.getElementById(
            "facility-status"
        ).value
    };

    const button = document.getElementById(
        "facility-save-button"
    );

    button.disabled = true;

    try {
        if (id) {
            await Api.put(
                `/api/v1/facilities/${id}`,
                data
            );

            showSuccess(
                "Đã cập nhật cơ sở vật chất"
            );
        } else {
            await Api.post(
                "/api/v1/facilities",
                {
                    branchId,
                    ...data
                }
            );

            showSuccess(
                "Đã tạo cơ sở vật chất"
            );
        }

        closeFacilityModal();
        await loadFacilities();
    } catch (error) {
        showFormError(
            formatApiError(error)
        );
    } finally {
        button.disabled = false;
    }
}

function ensureBranchOption(
    select,
    branchId
) {
    if (
        Array.from(select.options)
            .some(
                option =>
                    Number(option.value)
                    === branchId
            )
    ) {
        return;
    }

    const branch = branches.find(
        item => item.id === branchId
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

function facilityTypeName(type) {
    const names = {
        GYM_AREA: "Khu Gym",
        BOXING_ROOM: "Phòng Boxing",
        PICKLEBALL_COURT: "Sân Pickleball"
    };

    return names[type] || type;
}

function facilityStatusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "ACTIVE") {
        badge.classList.add(
            "badge-success"
        );
        badge.textContent = "Hoạt động";
    } else if (status === "MAINTENANCE") {
        badge.classList.add(
            "badge-warning"
        );
        badge.textContent = "Bảo trì";
    } else {
        badge.classList.add(
            "badge-muted"
        );
        badge.textContent = "Ngừng hoạt động";
    }

    return badge;
}

function branchName(id) {
    const branch = branches.find(
        item => item.id === id
    );

    return branch
        ? branch.name
        : `#${id}`;
}

function closeFacilityModal() {
    hide("facility-modal");
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

function show(id) {
    document
        .getElementById(id)
        .classList.remove("hidden");
}

function hide(id) {
    document
        .getElementById(id)
        .classList.add("hidden");
}

function showPageError(message) {
    const box = document.getElementById(
        "page-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function showFormError(message) {
    const box = document.getElementById(
        "facility-form-error"
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