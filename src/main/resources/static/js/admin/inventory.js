let products = [];
let branches = [];
let stockLevels = [];
let movements = [];
let users = [];

let currentTab = "PRODUCTS";
let currentStockBranchId = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user =
            Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        try {
            const results =
                await Promise.all([
                    Api.get("/api/v1/products"),
                    Api.get("/api/v1/branches"),
                    Api.get("/api/v1/users")
                ]);

            products = results[0];
            branches = results[1];
            users = results[2];

            renderBranchFilters();
            renderProducts();

            const activeBranch =
                branches.find(
                    branch =>
                        branch.status === "ACTIVE"
                );

            if (activeBranch) {
                currentStockBranchId =
                    activeBranch.id;

                document.getElementById(
                    "stock-branch-filter"
                ).value =
                    String(activeBranch.id);
            }
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
        "products-tab"
    ).addEventListener(
        "click",
        () => switchTab("PRODUCTS")
    );

    document.getElementById(
        "stock-tab"
    ).addEventListener(
        "click",
        () => switchTab("STOCK")
    );

    document.getElementById(
        "movements-tab"
    ).addEventListener(
        "click",
        () => switchTab("MOVEMENTS")
    );

    document.getElementById(
        "create-product-button"
    ).addEventListener(
        "click",
        openCreateProduct
    );

    document.getElementById(
        "product-modal-close"
    ).addEventListener(
        "click",
        closeProductModal
    );

    document.getElementById(
        "product-cancel-button"
    ).addEventListener(
        "click",
        closeProductModal
    );

    document.getElementById(
        "product-form"
    ).addEventListener(
        "submit",
        saveProduct
    );

    document.getElementById(
        "product-search"
    ).addEventListener(
        "input",
        renderProducts
    );

    document.getElementById(
        "product-status-filter"
    ).addEventListener(
        "change",
        renderProducts
    );

    document.getElementById(
        "stock-branch-filter"
    ).addEventListener(
        "change",
        async event => {
            currentStockBranchId =
                Number(event.target.value)
                || null;

            await loadStock();
        }
    );

    document.getElementById(
        "stock-search"
    ).addEventListener(
        "input",
        renderStock
    );

    document.getElementById(
        "movement-branch-filter"
    ).addEventListener(
        "change",
        loadMovements
    );

    document.getElementById(
        "refresh-movements"
    ).addEventListener(
        "click",
        loadMovements
    );

    document.getElementById(
        "adjust-modal-close"
    ).addEventListener(
        "click",
        closeAdjustModal
    );

    document.getElementById(
        "adjust-form"
    ).addEventListener(
        "submit",
        adjustStock
    );
}

async function switchTab(tab) {
    currentTab = tab;

    document.getElementById(
        "products-tab"
    ).classList.toggle(
        "active",
        tab === "PRODUCTS"
    );

    document.getElementById(
        "stock-tab"
    ).classList.toggle(
        "active",
        tab === "STOCK"
    );

    document.getElementById(
        "movements-tab"
    ).classList.toggle(
        "active",
        tab === "MOVEMENTS"
    );

    toggleSection(
        "products-section",
        tab === "PRODUCTS"
    );

    toggleSection(
        "stock-section",
        tab === "STOCK"
    );

    toggleSection(
        "movements-section",
        tab === "MOVEMENTS"
    );

    if (tab === "STOCK") {
        await loadStock();
    }

    if (tab === "MOVEMENTS") {
        await loadMovements();
    }
}

function renderBranchFilters() {
    const stockSelect =
        document.getElementById(
            "stock-branch-filter"
        );

    const movementSelect =
        document.getElementById(
            "movement-branch-filter"
        );

    stockSelect.replaceChildren();
    movementSelect.replaceChildren();

    const stockPlaceholder =
        document.createElement("option");

    stockPlaceholder.value = "";
    stockPlaceholder.textContent =
        "Chọn chi nhánh";

    stockSelect.appendChild(
        stockPlaceholder
    );

    const all =
        document.createElement("option");

    all.value = "";
    all.textContent =
        "Tất cả chi nhánh";

    movementSelect.appendChild(all);

    branches.forEach(branch => {
        const stockOption =
            document.createElement("option");

        stockOption.value =
            String(branch.id);

        stockOption.textContent =
            branch.name;

        stockSelect.appendChild(
            stockOption
        );

        const movementOption =
            stockOption.cloneNode(true);

        movementSelect.appendChild(
            movementOption
        );
    });
}

function renderProducts() {
    const body =
        document.getElementById(
            "product-table-body"
        );

    body.replaceChildren();

    const query =
        document.getElementById(
            "product-search"
        ).value
            .trim()
            .toLowerCase();

    const status =
        document.getElementById(
            "product-status-filter"
        ).value;

    const filtered =
        products.filter(product => {
            const searchMatch =
                !query
                || product.sku
                    .toLowerCase()
                    .includes(query)
                || product.name
                    .toLowerCase()
                    .includes(query)
                || product.category
                    .toLowerCase()
                    .includes(query);

            return searchMatch
                && (
                    !status
                    || product.status
                    === status
                );
        });

    if (!filtered.length) {
        appendEmptyRow(
            body,
            6,
            "Không có sản phẩm"
        );

        return;
    }

    filtered.forEach(product => {
        const row =
            document.createElement("tr");

        appendCell(
            row,
            product.sku
        );

        const productCell =
            document.createElement("td");

        const name =
            document.createElement("strong");

        name.textContent =
            product.name;

        productCell.appendChild(name);
        row.appendChild(productCell);

        appendCell(
            row,
            product.category
        );

        appendCell(
            row,
            formatMoney(
                product.price
            )
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            statusBadge(
                product.status
            )
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
            () => openEditProduct(
                product
            )
        );

        actions.appendChild(edit);
        row.appendChild(actions);

        body.appendChild(row);
    });
}

function openCreateProduct() {
    document.getElementById(
        "product-form"
    ).reset();

    document.getElementById(
        "product-id"
    ).value = "";

    document.getElementById(
        "product-modal-title"
    ).textContent =
        "Thêm sản phẩm";

    document.getElementById(
        "product-status-field"
    ).classList.add("hidden");

    hide("product-form-error");
    show("product-modal");
}

function openEditProduct(product) {
    document.getElementById(
        "product-id"
    ).value =
        product.id;

    document.getElementById(
        "product-modal-title"
    ).textContent =
        "Sửa sản phẩm";

    document.getElementById(
        "product-sku"
    ).value =
        product.sku;

    document.getElementById(
        "product-name"
    ).value =
        product.name;

    document.getElementById(
        "product-category"
    ).value =
        product.category;

    document.getElementById(
        "product-price"
    ).value =
        product.price;

    document.getElementById(
        "product-status"
    ).value =
        product.status;

    document.getElementById(
        "product-status-field"
    ).classList.remove("hidden");

    hide("product-form-error");
    show("product-modal");
}

async function saveProduct(event) {
    event.preventDefault();

    const id =
        document.getElementById(
            "product-id"
        ).value;

    const common = {
        sku:
            document.getElementById(
                "product-sku"
            ).value.trim(),

        name:
            document.getElementById(
                "product-name"
            ).value.trim(),

        category:
            document.getElementById(
                "product-category"
            ).value.trim(),

        price: Number(
            document.getElementById(
                "product-price"
            ).value
        )
    };

    const button =
        document.getElementById(
            "product-save-button"
        );

    button.disabled = true;

    try {
        let saved;

        if (id) {
            saved = await Api.put(
                `/api/v1/products/${id}`,
                {
                    ...common,
                    status:
                    document.getElementById(
                        "product-status"
                    ).value
                }
            );

            const index =
                products.findIndex(
                    product =>
                        product.id
                        === saved.id
                );

            if (index >= 0) {
                products[index] =
                    saved;
            }

            showSuccess(
                "Đã cập nhật sản phẩm"
            );
        } else {
            saved = await Api.post(
                "/api/v1/products",
                common
            );

            products.push(saved);

            showSuccess(
                "Đã tạo sản phẩm"
            );
        }

        closeProductModal();
        renderProducts();
    } catch (error) {
        const box =
            document.getElementById(
                "product-form-error"
            );

        box.textContent =
            formatApiError(error);

        box.classList.remove(
            "hidden"
        );
    } finally {
        button.disabled = false;
    }
}

async function loadStock() {
    if (!currentStockBranchId) {
        stockLevels = [];
        renderStock();
        return;
    }

    try {
        stockLevels = await Api.get(
            `/api/v1/inventory?branchId=${currentStockBranchId}`
        );

        renderStock();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function renderStock() {
    const body =
        document.getElementById(
            "stock-table-body"
        );

    body.replaceChildren();

    const query =
        document.getElementById(
            "stock-search"
        ).value
            .trim()
            .toLowerCase();

    const filtered =
        stockLevels.filter(stock =>
            !query
            || stock.sku
                .toLowerCase()
                .includes(query)
            || stock.productName
                .toLowerCase()
                .includes(query)
        );

    if (!filtered.length) {
        appendEmptyRow(
            body,
            5,
            currentStockBranchId
                ? "Không có dữ liệu tồn kho"
                : "Vui lòng chọn chi nhánh"
        );

        return;
    }

    filtered.forEach(stock => {
        const row =
            document.createElement("tr");

        appendCell(row, stock.sku);
        appendCell(
            row,
            stock.productName
        );

        const quantityCell =
            document.createElement("td");

        const badge =
            document.createElement("span");

        badge.className =
            stock.quantity > 0
                ? "badge badge-success"
                : "badge badge-danger";

        badge.textContent =
            String(stock.quantity);

        quantityCell.appendChild(badge);
        row.appendChild(quantityCell);

        appendCell(
            row,
            formatDateTime(
                stock.updatedAtUtc
            )
        );

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        const adjust =
            actionButton(
                "Điều chỉnh"
            );

        adjust.addEventListener(
            "click",
            () => openAdjustModal(
                stock
            )
        );

        actions.appendChild(adjust);
        row.appendChild(actions);

        body.appendChild(row);
    });
}

function openAdjustModal(stock) {
    document.getElementById(
        "adjust-product-id"
    ).value =
        stock.productId;

    document.getElementById(
        "adjust-product-name"
    ).textContent =
        `${stock.sku} - ${stock.productName}`
        + ` · Hiện có ${stock.quantity}`;

    document.getElementById(
        "adjust-delta"
    ).value = "";

    document.getElementById(
        "adjust-reason"
    ).value = "";

    hide("adjust-error");
    show("adjust-modal");
}

async function adjustStock(event) {
    event.preventDefault();

    const productId =
        Number(
            document.getElementById(
                "adjust-product-id"
            ).value
        );

    const quantityDelta =
        Number(
            document.getElementById(
                "adjust-delta"
            ).value
        );

    const reason =
        document.getElementById(
            "adjust-reason"
        ).value.trim();

    if (!quantityDelta) {
        showAdjustError(
            "Số lượng thay đổi phải khác 0"
        );

        return;
    }

    try {
        await Api.post(
            "/api/v1/inventory/adjust",
            {
                branchId:
                currentStockBranchId,

                productId,

                quantityDelta,

                reason
            }
        );

        closeAdjustModal();

        showSuccess(
            "Đã điều chỉnh tồn kho"
        );

        await loadStock();

        if (currentTab === "MOVEMENTS") {
            await loadMovements();
        }
    } catch (error) {
        showAdjustError(
            formatApiError(error)
        );
    }
}

async function loadMovements() {
    const branchId =
        document.getElementById(
            "movement-branch-filter"
        ).value;

    try {
        movements = await Api.get(
            branchId
                ? `/api/v1/inventory/movements?branchId=${branchId}`
                : "/api/v1/inventory/movements"
        );

        renderMovements();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function renderMovements() {
    const body =
        document.getElementById(
            "movement-table-body"
        );

    body.replaceChildren();

    if (!movements.length) {
        appendEmptyRow(
            body,
            6,
            "Chưa có biến động kho"
        );

        return;
    }

    movements.forEach(movement => {
        const row =
            document.createElement("tr");

        appendCell(
            row,
            formatDateTime(
                movement.createdAtUtc
            )
        );

        appendCell(
            row,
            branchName(
                movement.branchId
            )
        );

        appendCell(
            row,
            productName(
                movement.productId
            )
        );

        const deltaCell =
            document.createElement("td");

        const delta =
            document.createElement("strong");

        delta.className =
            movement.quantityDelta > 0
                ? "inventory-positive"
                : "inventory-negative";

        delta.textContent =
            movement.quantityDelta > 0
                ? `+${movement.quantityDelta}`
                : String(
                    movement.quantityDelta
                );

        deltaCell.appendChild(delta);
        row.appendChild(deltaCell);

        appendCell(
            row,
            movement.reason
        );

        appendCell(
            row,
            userName(
                movement.performedByUserId
            )
        );

        body.appendChild(row);
    });
}

function branchName(id) {
    const branch =
        branches.find(
            item => item.id === id
        );

    return branch
        ? branch.name
        : `Branch #${id}`;
}

function productName(id) {
    const product =
        products.find(
            item => item.id === id
        );

    return product
        ? product.name
        : `Product #${id}`;
}

function userName(id) {
    const user =
        users.find(
            item => item.id === id
        );

    return user
        ? user.fullName
        : `User #${id}`;
}

function statusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className =
        status === "ACTIVE"
            ? "badge badge-success"
            : "badge badge-muted";

    badge.textContent =
        status === "ACTIVE"
            ? "Hoạt động"
            : "Ngừng hoạt động";

    return badge;
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

function toggleSection(
    id,
    visible
) {
    document.getElementById(id)
        .classList.toggle(
        "hidden",
        !visible
    );
}

function closeProductModal() {
    hide("product-modal");
}

function closeAdjustModal() {
    hide("adjust-modal");
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
    cell.className = "empty-cell";
    cell.textContent = message;

    row.appendChild(cell);
    body.appendChild(row);
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

function show(id) {
    document.getElementById(id)
        .classList.remove("hidden");
}

function hide(id) {
    document.getElementById(id)
        .classList.add("hidden");
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

function showAdjustError(message) {
    const box =
        document.getElementById(
            "adjust-error"
        );

    box.textContent = message;

    box.classList.remove("hidden");
}

function formatApiError(error) {
    if (
        error.errors
        && Object.keys(
            error.errors
        ).length
    ) {
        return Object.values(
            error.errors
        ).join(". ");
    }

    return error.message
        || "Có lỗi xảy ra";
}