let branches = [];
let plans = [];
let branchServices = new Map();

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        await loadBranches();
        await loadPlans();
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
        .getElementById("create-plan-button")
        .addEventListener(
            "click",
            openCreatePlan
        );

    document
        .getElementById("plan-modal-close")
        .addEventListener(
            "click",
            closePlanModal
        );

    document
        .getElementById("plan-cancel-button")
        .addEventListener(
            "click",
            closePlanModal
        );

    document
        .getElementById("plan-form")
        .addEventListener(
            "submit",
            savePlan
        );

    document
        .getElementById("plan-search")
        .addEventListener(
            "input",
            renderPlans
        );

    document
        .getElementById("branch-filter")
        .addEventListener(
            "change",
            loadPlans
        );

    document
        .getElementById("status-filter")
        .addEventListener(
            "change",
            loadPlans
        );

    document
        .getElementById("plan-branch")
        .addEventListener(
            "change",
            async event => {
                const branchId =
                    Number(event.target.value);

                if (branchId) {
                    await configureServicePicker(
                        branchId,
                        []
                    );
                }
            }
        );
}

async function loadBranches() {
    try {
        branches = await Api.get(
            "/api/v1/branches"
        );

        renderBranchSelectors();
    } catch (error) {
        showPageError(error.message);
    }
}

function renderBranchSelectors() {
    const filter = document.getElementById(
        "branch-filter"
    );

    const formSelect = document.getElementById(
        "plan-branch"
    );

    filter.replaceChildren();
    formSelect.replaceChildren();

    const all =
        document.createElement("option");

    all.value = "";
    all.textContent = "Tất cả chi nhánh";

    filter.appendChild(all);

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent =
        "Chọn chi nhánh";

    formSelect.appendChild(placeholder);

    branches.forEach(branch => {
        const filterOption =
            document.createElement("option");

        filterOption.value =
            String(branch.id);

        filterOption.textContent =
            branch.name;

        filter.appendChild(filterOption);

        if (branch.status === "ACTIVE") {
            const formOption =
                document.createElement("option");

            formOption.value =
                String(branch.id);

            formOption.textContent =
                branch.name;

            formSelect.appendChild(
                formOption
            );
        }
    });
}

async function loadPlans() {
    hide("page-error");

    const branchId = document
        .getElementById("branch-filter")
        .value;

    const status = document
        .getElementById("status-filter")
        .value;

    const params = new URLSearchParams();

    if (branchId) {
        params.set("branchId", branchId);
    }

    if (status) {
        params.set("status", status);
    }

    const query = params.toString();

    try {
        plans = await Api.get(
            query
                ? `/api/v1/plans?${query}`
                : "/api/v1/plans"
        );

        renderPlans();
    } catch (error) {
        showPageError(error.message);
    }
}

function renderPlans() {
    const body = document.getElementById(
        "plan-table-body"
    );

    body.replaceChildren();

    const search = document
        .getElementById("plan-search")
        .value
        .trim()
        .toLowerCase();

    const filtered = plans.filter(plan => {
        if (!search) {
            return true;
        }

        return [
            plan.planCode,
            plan.name,
            plan.description
        ].some(value =>
            String(value || "")
                .toLowerCase()
                .includes(search)
        );
    });

    if (!filtered.length) {
        appendEmptyRow(
            body,
            9,
            "Không tìm thấy gói tập"
        );

        return;
    }

    filtered.forEach(plan => {
        const row =
            document.createElement("tr");

        appendCell(row, plan.planCode);

        const nameCell =
            document.createElement("td");

        const name =
            document.createElement("strong");

        name.textContent = plan.name;

        const description =
            document.createElement("div");

        description.className =
            "muted small table-description";

        description.textContent =
            plan.description || "Không có mô tả";

        nameCell.append(
            name,
            description
        );

        row.appendChild(nameCell);

        appendCell(
            row,
            branchName(plan.branchId)
        );

        const tierCell =
            document.createElement("td");

        tierCell.appendChild(
            createTierBadge(plan.tier)
        );

        row.appendChild(tierCell);

        appendCell(
            row,
            `${plan.durationDays} ngày`
        );

        appendCell(
            row,
            formatMoney(plan.price)
        );

        const serviceCell =
            document.createElement("td");

        const serviceBadges =
            document.createElement("div");

        serviceBadges.className =
            "service-badges";

        plan.services.forEach(service => {
            const badge =
                document.createElement("span");

            badge.className =
                "badge badge-yellow";

            badge.textContent = service;

            serviceBadges.appendChild(
                badge
            );
        });

        serviceCell.appendChild(
            serviceBadges
        );

        row.appendChild(serviceCell);

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            createStatusBadge(plan.status)
        );

        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        const edit =
            actionButton("Sửa");

        edit.addEventListener(
            "click",
            () => openEditPlan(plan)
        );

        actions.appendChild(edit);
        row.appendChild(actions);

        body.appendChild(row);
    });
}

async function openCreatePlan() {
    document.getElementById(
        "plan-form"
    ).reset();

    document.getElementById(
        "plan-id"
    ).value = "";

    document.getElementById(
        "plan-modal-title"
    ).textContent = "Thêm gói tập";

    document.getElementById(
        "plan-status-field"
    ).classList.add("hidden");

    document.getElementById(
        "plan-branch-field"
    ).classList.remove("hidden");

    document.getElementById(
        "plan-branch"
    ).disabled = false;

    resetServicePicker();

    hide("plan-form-error");
    show("plan-modal");
}

async function openEditPlan(plan) {
    document.getElementById(
        "plan-id"
    ).value = plan.id;

    document.getElementById(
        "plan-modal-title"
    ).textContent = "Sửa gói tập";

    const branchSelect =
        document.getElementById(
            "plan-branch"
        );

    ensureBranchOption(
        branchSelect,
        plan.branchId
    );

    branchSelect.value =
        String(plan.branchId);

    branchSelect.disabled = true;

    document.getElementById(
        "plan-code"
    ).value = plan.planCode;

    document.getElementById(
        "plan-name"
    ).value = plan.name;

    document.getElementById(
        "plan-tier"
    ).value = plan.tier;

    document.getElementById(
        "plan-duration"
    ).value = plan.durationDays;

    document.getElementById(
        "plan-price"
    ).value = plan.price;

    document.getElementById(
        "plan-description"
    ).value = plan.description || "";

    document.getElementById(
        "plan-status"
    ).value = plan.status;

    document.getElementById(
        "plan-status-field"
    ).classList.remove("hidden");

    document.getElementById(
        "plan-branch-field"
    ).classList.remove("hidden");

    hide("plan-form-error");
    show("plan-modal");

    try {
        await configureServicePicker(
            plan.branchId,
            plan.services
        );
    } catch (error) {
        showFormError(error.message);
    }
}

function ensureBranchOption(
    select,
    branchId
) {
    const exists = Array
        .from(select.options)
        .some(option =>
            option.value === String(branchId)
        );

    if (exists) {
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

    option.value = String(branch.id);
    option.textContent =
        `${branch.name} (Ngừng hoạt động)`;

    select.appendChild(option);
}

async function configureServicePicker(
    branchId,
    selectedServices
) {
    const available =
        await getBranchServices(branchId);

    const supported = new Set(
        available.map(
            item => item.serviceCode
        )
    );

    document
        .querySelectorAll(
            "#plan-services .service-option"
        )
        .forEach(option => {
            const service =
                option.dataset.service;

            const input =
                option.querySelector("input");

            const enabled =
                supported.has(service);

            input.disabled = !enabled;

            input.checked =
                enabled
                && selectedServices.includes(
                    service
                );

            option.classList.toggle(
                "service-option-disabled",
                !enabled
            );
        });
}

async function getBranchServices(branchId) {
    if (branchServices.has(branchId)) {
        return branchServices.get(branchId);
    }

    const services = await Api.get(
        `/api/v1/branches/${branchId}/services`
    );

    branchServices.set(
        branchId,
        services
    );

    return services;
}

function resetServicePicker() {
    document
        .querySelectorAll(
            "#plan-services .service-option"
        )
        .forEach(option => {
            const input =
                option.querySelector("input");

            input.checked = false;
            input.disabled = true;

            option.classList.add(
                "service-option-disabled"
            );
        });
}

async function savePlan(event) {
    event.preventDefault();

    const id = document
        .getElementById("plan-id")
        .value;

    const branchId = Number(
        document
            .getElementById("plan-branch")
            .value
    );

    const services = Array
        .from(
            document.querySelectorAll(
                "#plan-services input:checked"
            )
        )
        .map(input => input.value);

    if (!services.length) {
        showFormError(
            "Vui lòng chọn ít nhất một dịch vụ"
        );
        return;
    }

    const common = {
        planCode: document
            .getElementById("plan-code")
            .value
            .trim(),

        name: document
            .getElementById("plan-name")
            .value
            .trim(),

        tier: document
            .getElementById("plan-tier")
            .value,

        durationDays: Number(
            document
                .getElementById("plan-duration")
                .value
        ),

        price: Number(
            document
                .getElementById("plan-price")
                .value
        ),

        description: nullable(
            document
                .getElementById("plan-description")
                .value
        ),

        services
    };

    const button =
        document.getElementById(
            "plan-save-button"
        );

    button.disabled = true;

    try {
        if (id) {
            await Api.put(
                `/api/v1/plans/${id}`,
                {
                    ...common,
                    status: document
                        .getElementById("plan-status")
                        .value
                }
            );

            showSuccess(
                "Đã cập nhật gói tập"
            );
        } else {
            if (!branchId) {
                showFormError(
                    "Vui lòng chọn chi nhánh"
                );
                return;
            }

            await Api.post(
                "/api/v1/plans",
                {
                    branchId,
                    ...common
                }
            );

            showSuccess(
                "Đã tạo gói tập"
            );
        }

        closePlanModal();

        branchServices.clear();

        await loadPlans();
    } catch (error) {
        showFormError(
            formatApiError(error)
        );
    } finally {
        button.disabled = false;
    }
}

function closePlanModal() {
    hide("plan-modal");
}

function branchName(branchId) {
    const branch = branches.find(
        item => item.id === branchId
    );

    return branch
        ? branch.name
        : `#${branchId}`;
}

function createTierBadge(tier) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    switch (tier) {
        case "PREMIUM":
            badge.classList.add(
                "badge-premium"
            );
            break;

        case "STANDARD":
            badge.classList.add(
                "badge-standard"
            );
            break;

        default:
            badge.classList.add(
                "badge-basic"
            );
    }

    badge.textContent = tier;

    return badge;
}

function createStatusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "ACTIVE") {
        badge.classList.add(
            "badge-success"
        );

        badge.textContent = "Hoạt động";
    } else {
        badge.classList.add(
            "badge-muted"
        );

        badge.textContent =
            "Ngừng hoạt động";
    }

    return badge;
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

function nullable(value) {
    const normalized =
        String(value || "").trim();

    return normalized || null;
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
        .classList
        .remove("hidden");
}

function hide(id) {
    document
        .getElementById(id)
        .classList
        .add("hidden");
}

function showPageError(message) {
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}

function showFormError(message) {
    const box =
        document.getElementById(
            "plan-form-error"
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
        () => box.classList.add("hidden"),
        3000
    );
}

function formatApiError(error) {
    if (error.errors
        && Object.keys(error.errors).length) {

        return Object.values(
            error.errors
        ).join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}