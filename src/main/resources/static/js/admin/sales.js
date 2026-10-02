let branches = [];
let members = [];
let plans = [];
let products = [];
let inventory = [];
let orders = [];

let cart = [];
let currentPayment = null;
let currentOrder = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        await loadInitialData();
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
        .getElementById("sales-branch")
        .addEventListener(
            "change",
            async () => {
                clearCart();
                await loadCatalog();
            }
        );

    document
        .getElementById("plans-tab")
        .addEventListener(
            "click",
            () => switchCatalog("PLAN")
        );

    document
        .getElementById("products-tab")
        .addEventListener(
            "click",
            () => switchCatalog("PRODUCT")
        );

    document
        .getElementById("clear-cart-button")
        .addEventListener(
            "click",
            clearCart
        );

    document
        .getElementById("checkout-button")
        .addEventListener(
            "click",
            checkout
        );

    document
        .getElementById("payment-success-button")
        .addEventListener(
            "click",
            simulateSuccess
        );

    document
        .getElementById("payment-failure-button")
        .addEventListener(
            "click",
            simulateFailure
        );

    document
        .getElementById("refresh-orders-button")
        .addEventListener(
            "click",
            loadOrders
        );
}

async function loadInitialData() {
    try {
        const results = await Promise.all([
            Api.get("/api/v1/branches?status=ACTIVE"),
            Api.get("/api/v1/members?status=ACTIVE"),
            Api.get("/api/v1/products?status=ACTIVE")
        ]);

        branches = results[0];
        members = results[1];
        products = results[2];

        renderBranchOptions();
        renderMemberOptions();

        if (branches.length) {
            document.getElementById(
                "sales-branch"
            ).value = String(branches[0].id);

            await loadCatalog();
        }

        await loadOrders();
    } catch (error) {
        showError(error.message);
    }
}

function renderBranchOptions() {
    const select = document.getElementById(
        "sales-branch"
    );

    select.replaceChildren();

    branches.forEach(branch => {
        const option = document.createElement("option");

        option.value = String(branch.id);
        option.textContent = branch.name;

        select.appendChild(option);
    });
}

function renderMemberOptions() {
    const select = document.getElementById(
        "sales-member"
    );

    select.replaceChildren();

    const guest = document.createElement("option");

    guest.value = "";
    guest.textContent = "Khách lẻ";

    select.appendChild(guest);

    members.forEach(member => {
        const option = document.createElement("option");

        option.value = String(member.id);
        option.textContent =
            `${member.memberCode} - ${member.fullName}`;

        select.appendChild(option);
    });
}

async function loadCatalog() {
    const branchId = currentBranchId();

    if (!branchId) {
        return;
    }

    show("catalog-loading");

    try {
        const results = await Promise.all([
            Api.get(
                `/api/v1/plans?branchId=${branchId}&status=ACTIVE`
            ),
            Api.get(
                `/api/v1/inventory?branchId=${branchId}`
            )
        ]);

        plans = results[0];
        inventory = results[1];

        renderPlans();
        renderProducts();
    } catch (error) {
        showError(error.message);
    } finally {
        hide("catalog-loading");
    }
}

function renderPlans() {
    const container = document.getElementById(
        "plan-catalog"
    );

    container.replaceChildren();

    if (!plans.length) {
        container.appendChild(
            emptyElement("Không có gói tập")
        );

        return;
    }

    plans.forEach(plan => {
        const card = document.createElement("article");
        card.className = "catalog-card";

        const header = document.createElement("div");
        header.className = "catalog-card-header";

        const title = document.createElement("strong");
        title.textContent = plan.name;

        const tier = document.createElement("span");
        tier.className = "badge badge-yellow";
        tier.textContent = plan.tier;

        header.append(title, tier);

        const code = document.createElement("div");
        code.className = "muted small";
        code.textContent = plan.planCode;

        const duration = document.createElement("div");
        duration.className = "catalog-meta";
        duration.textContent =
            `${plan.durationDays} ngày · `
            + plan.services.join(", ");

        const footer = document.createElement("div");
        footer.className = "catalog-card-footer";

        const price = document.createElement("strong");
        price.className = "catalog-price";
        price.textContent = formatMoney(plan.price);

        const add = document.createElement("button");

        add.type = "button";
        add.className = "button button-primary button-small";
        add.textContent = "Thêm";

        add.addEventListener(
            "click",
            () => addPlan(plan)
        );

        footer.append(price, add);

        card.append(
            header,
            code,
            duration,
            footer
        );

        container.appendChild(card);
    });
}

function renderProducts() {
    const container = document.getElementById(
        "product-catalog"
    );

    container.replaceChildren();

    if (!products.length) {
        container.appendChild(
            emptyElement("Không có sản phẩm")
        );

        return;
    }

    products.forEach(product => {
        const stock = findStock(product.id);

        const card = document.createElement("article");

        card.className = "catalog-card";

        const title = document.createElement("strong");
        title.textContent = product.name;

        const sku = document.createElement("div");
        sku.className = "muted small";
        sku.textContent =
            `${product.sku} · ${product.category}`;

        const stockText = document.createElement("div");

        stockText.className =
            stock > 0
                ? "stock-text"
                : "stock-text stock-empty";

        stockText.textContent =
            `Tồn kho: ${stock}`;

        const footer = document.createElement("div");
        footer.className = "catalog-card-footer";

        const price = document.createElement("strong");
        price.className = "catalog-price";
        price.textContent =
            formatMoney(product.price);

        const add = document.createElement("button");

        add.type = "button";
        add.className =
            "button button-primary button-small";

        add.textContent = "Thêm";
        add.disabled = stock <= 0;

        add.addEventListener(
            "click",
            () => addProduct(product)
        );

        footer.append(price, add);

        card.append(
            title,
            sku,
            stockText,
            footer
        );

        container.appendChild(card);
    });
}

function addPlan(plan) {
    const existingPlan = cart.find(
        item => item.itemType === "PLAN"
    );

    if (existingPlan) {
        showError(
            "Mỗi đơn hàng chỉ được có tối đa một gói tập"
        );
        return;
    }

    const memberId = currentMemberId();

    if (!memberId) {
        showError(
            "Phải chọn hội viên trước khi bán gói tập"
        );
        return;
    }

    cart.push({
        itemType: "PLAN",
        planId: plan.id,
        productId: null,
        name: plan.name,
        unitPrice: Number(plan.price),
        quantity: 1
    });

    renderCart();
}

function addProduct(product) {
    const stock = findStock(product.id);

    const item = cart.find(
        value =>
            value.itemType === "PRODUCT"
            && value.productId === product.id
    );

    if (item) {
        if (item.quantity >= stock) {
            showError("Số lượng vượt tồn kho hiện tại");
            return;
        }

        item.quantity++;
    } else {
        if (stock <= 0) {
            showError("Sản phẩm đã hết hàng");
            return;
        }

        cart.push({
            itemType: "PRODUCT",
            planId: null,
            productId: product.id,
            name: product.name,
            unitPrice: Number(product.price),
            quantity: 1
        });
    }

    renderCart();
}

function renderCart() {
    const container = document.getElementById(
        "cart-items"
    );

    container.replaceChildren();

    if (!cart.length) {
        container.appendChild(
            emptyElement("Chưa có sản phẩm")
        );

        updateTotals();
        return;
    }

    cart.forEach((item, index) => {
        const row = document.createElement("div");

        row.className = "cart-item";

        const info = document.createElement("div");

        const name = document.createElement("strong");
        name.textContent = item.name;

        const type = document.createElement("div");
        type.className = "muted small";

        type.textContent =
            item.itemType === "PLAN"
                ? "Gói tập"
                : formatMoney(item.unitPrice);

        info.append(name, type);

        const controls = document.createElement("div");
        controls.className = "cart-item-controls";

        if (item.itemType === "PRODUCT") {
            const minus = cartControlButton("−");

            minus.addEventListener(
                "click",
                () => changeQuantity(index, -1)
            );

            const quantity = document.createElement("span");
            quantity.textContent =
                String(item.quantity);

            const plus = cartControlButton("+");

            plus.addEventListener(
                "click",
                () => changeQuantity(index, 1)
            );

            controls.append(
                minus,
                quantity,
                plus
            );
        }

        const lineTotal = document.createElement("strong");

        lineTotal.textContent = formatMoney(
            item.unitPrice * item.quantity
        );

        const remove = cartControlButton("×");

        remove.classList.add("cart-remove");

        remove.addEventListener(
            "click",
            () => {
                cart.splice(index, 1);
                renderCart();
            }
        );

        controls.append(
            lineTotal,
            remove
        );

        row.append(info, controls);
        container.appendChild(row);
    });

    updateTotals();
}

function changeQuantity(index, delta) {
    const item = cart[index];

    if (!item || item.itemType !== "PRODUCT") {
        return;
    }

    const newQuantity =
        item.quantity + delta;

    if (newQuantity <= 0) {
        cart.splice(index, 1);
        renderCart();
        return;
    }

    const stock = findStock(item.productId);

    if (newQuantity > stock) {
        showError(
            "Số lượng vượt tồn kho hiện tại"
        );
        return;
    }

    item.quantity = newQuantity;

    renderCart();
}

function updateTotals() {
    const total = cart.reduce(
        (sum, item) =>
            sum + item.unitPrice * item.quantity,
        0
    );

    document.getElementById(
        "cart-subtotal"
    ).textContent = formatMoney(total);

    document.getElementById(
        "cart-total"
    ).textContent = formatMoney(total);
}

function clearCart() {
    cart = [];
    renderCart();
}

async function checkout() {
    hide("page-error");

    if (!cart.length) {
        showError("Giỏ hàng đang trống");
        return;
    }

    const branchId = currentBranchId();
    const memberId = currentMemberId();

    if (cart.some(
        item => item.itemType === "PLAN"
    ) && !memberId) {
        showError(
            "Đơn mua gói tập bắt buộc phải có hội viên"
        );
        return;
    }

    const button = document.getElementById(
        "checkout-button"
    );

    button.disabled = true;

    try {
        currentOrder = await Api.post(
            "/api/v1/orders",
            {
                branchId,
                memberId,
                items: cart.map(item => ({
                    itemType: item.itemType,
                    planId: item.planId,
                    productId: item.productId,
                    quantity: item.quantity
                }))
            }
        );

        currentPayment = await Api.post(
            "/api/v1/payments/momo",
            {
                orderId: currentOrder.id,
                idempotencyKey:
                    generateIdempotencyKey(
                        currentOrder.id
                    )
            }
        );

        openPaymentModal();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    } finally {
        button.disabled = false;
    }
}

function openPaymentModal() {
    document.getElementById(
        "payment-order-code"
    ).textContent = currentOrder.orderCode;

    document.getElementById(
        "payment-amount"
    ).textContent = formatMoney(
        currentPayment.amount
    );

    document.getElementById(
        "payment-code"
    ).textContent =
        currentPayment.paymentCode;

    hide("payment-error");
    show("payment-modal");
}

async function simulateSuccess() {
    if (!currentPayment) {
        return;
    }

    setPaymentButtonsDisabled(true);

    try {
        const payment = await Api.post(
            `/api/v1/payments/${currentPayment.id}/simulate-success`,
            {}
        );

        if (payment.status !== "SUCCEEDED") {
            throw new Error(
                "Thanh toán chưa thành công"
            );
        }

        hide("payment-modal");

        showSuccess(
            "Thanh toán thành công"
        );

        currentPayment = null;
        currentOrder = null;

        clearCart();

        await Promise.all([
            loadCatalog(),
            loadOrders()
        ]);
    } catch (error) {
        showPaymentError(
            formatApiError(error)
        );
    } finally {
        setPaymentButtonsDisabled(false);
    }
}

async function simulateFailure() {
    if (!currentPayment) {
        return;
    }

    setPaymentButtonsDisabled(true);

    try {
        const payment = await Api.post(
            `/api/v1/payments/${currentPayment.id}/simulate-failure`,
            {}
        );

        hide("payment-modal");

        showError(
            payment.status === "FAILED"
                ? "Thanh toán thất bại"
                : "Không thể cập nhật thanh toán"
        );

        currentPayment = null;
        currentOrder = null;

        clearCart();

        await loadOrders();
    } catch (error) {
        showPaymentError(
            formatApiError(error)
        );
    } finally {
        setPaymentButtonsDisabled(false);
    }
}

async function loadOrders() {
    try {
        orders = await Api.get(
            "/api/v1/orders"
        );

        renderOrders();
    } catch (error) {
        showError(error.message);
    }
}

function renderOrders() {
    const body = document.getElementById(
        "orders-table-body"
    );

    body.replaceChildren();

    if (!orders.length) {
        appendEmptyRow(
            body,
            6,
            "Chưa có đơn hàng"
        );
        return;
    }

    orders.slice(0, 20).forEach(order => {
        const row = document.createElement("tr");

        appendCell(row, order.orderCode);
        appendCell(
            row,
            branchName(order.branchId)
        );
        appendCell(
            row,
            memberName(order.memberId)
        );
        appendCell(
            row,
            formatMoney(order.total)
        );

        const statusCell = document.createElement("td");

        statusCell.appendChild(
            orderStatusBadge(order.status)
        );

        row.appendChild(statusCell);

        appendCell(
            row,
            formatDateTime(order.createdAtUtc)
        );

        const invoiceCell =
            document.createElement("td");

        invoiceCell.className =
            "table-actions";

        if (order.status === "PAID") {
            const invoiceButton =
                actionButton("Hóa đơn");

            invoiceButton.addEventListener(
                "click",
                async () => {
                    invoiceButton.disabled = true;

                    try {
                        await Invoice.download(order);
                    } catch (error) {
                        showError(
                            formatApiError(error)
                        );
                    } finally {
                        invoiceButton.disabled = false;
                    }
                }
            );

            invoiceCell.appendChild(
                invoiceButton
            );
        } else {
            invoiceCell.textContent = "—";
        }

        row.appendChild(invoiceCell);

        body.appendChild(row);
    });
}

function switchCatalog(type) {
    const planTab = document.getElementById(
        "plans-tab"
    );

    const productTab = document.getElementById(
        "products-tab"
    );

    if (type === "PLAN") {
        planTab.classList.add("active");
        productTab.classList.remove("active");

        show("plan-catalog");
        hide("product-catalog");
    } else {
        productTab.classList.add("active");
        planTab.classList.remove("active");

        show("product-catalog");
        hide("plan-catalog");
    }
}

function findStock(productId) {
    const stock = inventory.find(
        item => item.productId === productId
    );

    return stock ? Number(stock.quantity) : 0;
}

function currentBranchId() {
    const value = document.getElementById(
        "sales-branch"
    ).value;

    return value ? Number(value) : null;
}

function currentMemberId() {
    const value = document.getElementById(
        "sales-member"
    ).value;

    return value ? Number(value) : null;
}

function branchName(id) {
    const branch = branches.find(
        item => item.id === id
    );

    return branch
        ? branch.name
        : `#${id}`;
}

function memberName(id) {
    if (id == null) {
        return "Khách lẻ";
    }

    const member = members.find(
        item => item.id === id
    );

    return member
        ? member.fullName
        : `#${id}`;
}

function orderStatusBadge(status) {
    const badge = document.createElement("span");

    badge.className = "badge";

    if (status === "PAID") {
        badge.classList.add("badge-success");
        badge.textContent = "Đã thanh toán";
    } else if (status === "CANCELLED") {
        badge.classList.add("badge-danger");
        badge.textContent = "Đã hủy";
    } else {
        badge.classList.add("badge-warning");
        badge.textContent = "Chờ thanh toán";
    }

    return badge;
}

function generateIdempotencyKey(orderId) {
    if (window.crypto?.randomUUID) {
        return `web-${orderId}-${crypto.randomUUID()}`;
    }

    return `web-${orderId}-${Date.now()}-${Math.random()}`;
}

function cartControlButton(text) {
    const button = document.createElement("button");

    button.type = "button";
    button.className = "cart-control";
    button.textContent = text;

    return button;
}

function emptyElement(text) {
    const div = document.createElement("div");

    div.className = "empty-state";
    div.textContent = text;

    return div;
}

function appendCell(row, value) {
    const cell = document.createElement("td");

    cell.textContent = value ?? "—";

    row.appendChild(cell);
}

function appendEmptyRow(body, colspan, message) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");

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
            timeZone: "Asia/Ho_Chi_Minh"
        }
    ).format(new Date(value));
}

function setPaymentButtonsDisabled(disabled) {
    document.getElementById(
        "payment-success-button"
    ).disabled = disabled;

    document.getElementById(
        "payment-failure-button"
    ).disabled = disabled;
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

function showPaymentError(message) {
    const box = document.getElementById(
        "payment-error"
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

function actionButton(text) {
    const button =
        document.createElement("button");

    button.type = "button";

    button.className =
        "button button-small button-secondary";

    button.textContent = text;

    return button;
}