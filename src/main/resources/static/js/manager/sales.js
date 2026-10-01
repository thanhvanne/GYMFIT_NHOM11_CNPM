let currentUser;
let branch;
let members = [];
let plans = [];
let products = [];
let inventory = [];
let orders = [];
let cart = [];

let currentOrder = null;
let currentPayment = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser =
            Auth.requireRole("BRANCH_MANAGER");

        if (!currentUser) {
            return;
        }

        bindEvents();

        try {
            const results = await Promise.all([
                Api.get(
                    `/api/v1/branches/${currentUser.branchId}`
                ),
                Api.get(
                    "/api/v1/members?status=ACTIVE"
                ),
                Api.get(
                    "/api/v1/plans?status=ACTIVE"
                ),
                Api.get(
                    "/api/v1/products?status=ACTIVE"
                ),
                Api.get(
                    "/api/v1/inventory"
                ),
                Api.get(
                    "/api/v1/orders"
                )
            ]);

            branch = results[0];
            members = results[1];
            plans = results[2];
            products = results[3];
            inventory = results[4];
            orders = results[5];

            document.getElementById(
                "branch-name"
            ).textContent = branch.name;

            renderMembers();
            renderPlans();
            renderProducts();
            renderCart();
            renderOrders();
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
        "plans-tab"
    ).addEventListener(
        "click",
        () => switchTab("PLAN")
    );

    document.getElementById(
        "products-tab"
    ).addEventListener(
        "click",
        () => switchTab("PRODUCT")
    );

    document.getElementById(
        "clear-cart-button"
    ).addEventListener(
        "click",
        clearCart
    );

    document.getElementById(
        "checkout-button"
    ).addEventListener(
        "click",
        checkout
    );

    document.getElementById(
        "payment-success"
    ).addEventListener(
        "click",
        paymentSuccess
    );

    document.getElementById(
        "payment-failure"
    ).addEventListener(
        "click",
        paymentFailure
    );
}

function renderMembers() {
    const select = document.getElementById(
        "sales-member"
    );

    members.forEach(member => {
        const option =
            document.createElement("option");

        option.value = member.id;

        option.textContent =
            `${member.memberCode} - ${member.fullName}`;

        select.appendChild(option);
    });
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
        const card =
            catalogCard(
                plan.name,
                plan.planCode,
                formatMoney(plan.price)
            );

        const services =
            document.createElement("div");

        services.className = "catalog-meta";
        services.textContent =
            `${plan.durationDays} ngày · `
            + plan.services.join(", ");

        const button =
            addButton();

        button.addEventListener(
            "click",
            () => addPlan(plan)
        );

        card.append(
            services,
            button
        );

        container.appendChild(card);
    });
}

function renderProducts() {
    const container = document.getElementById(
        "product-catalog"
    );

    container.replaceChildren();

    products.forEach(product => {
        const stock = productStock(
            product.id
        );

        const card = catalogCard(
            product.name,
            product.sku,
            formatMoney(product.price)
        );

        const stockElement =
            document.createElement("div");

        stockElement.className =
            stock > 0
                ? "stock-text"
                : "stock-text stock-empty";

        stockElement.textContent =
            `Tồn kho: ${stock}`;

        const button = addButton();

        button.disabled = stock <= 0;

        button.addEventListener(
            "click",
            () => addProduct(product)
        );

        card.append(
            stockElement,
            button
        );

        container.appendChild(card);
    });
}

function catalogCard(
    name,
    subtitle,
    price
) {
    const card =
        document.createElement("article");

    card.className = "catalog-card";

    const title =
        document.createElement("strong");

    title.textContent = name;

    const sub =
        document.createElement("div");

    sub.className = "muted small";
    sub.textContent = subtitle;

    const priceElement =
        document.createElement("div");

    priceElement.className =
        "manager-plan-price";

    priceElement.textContent = price;

    card.append(
        title,
        sub,
        priceElement
    );

    return card;
}

function addButton() {
    const button =
        document.createElement("button");

    button.type = "button";

    button.className =
        "button button-primary button-small";

    button.textContent = "Thêm";

    return button;
}

function addPlan(plan) {
    if (!selectedMemberId()) {
        showError(
            "Phải chọn hội viên trước khi bán gói tập"
        );
        return;
    }

    if (
        cart.some(
            item => item.itemType === "PLAN"
        )
    ) {
        showError(
            "Mỗi đơn chỉ được có một gói tập"
        );
        return;
    }

    cart.push({
        itemType: "PLAN",
        planId: plan.id,
        productId: null,
        name: plan.name,
        price: Number(plan.price),
        quantity: 1
    });

    renderCart();
}

function addProduct(product) {
    const stock =
        productStock(product.id);

    const existing =
        cart.find(
            item =>
                item.itemType === "PRODUCT"
                && item.productId === product.id
        );

    if (existing) {
        if (existing.quantity >= stock) {
            showError(
                "Số lượng vượt tồn kho"
            );
            return;
        }

        existing.quantity++;
    } else {
        if (stock <= 0) {
            showError(
                "Sản phẩm đã hết hàng"
            );
            return;
        }

        cart.push({
            itemType: "PRODUCT",
            planId: null,
            productId: product.id,
            name: product.name,
            price: Number(product.price),
            quantity: 1
        });
    }

    renderCart();
}

function renderCart() {
    const container =
        document.getElementById(
            "cart-items"
        );

    container.replaceChildren();

    if (!cart.length) {
        container.appendChild(
            emptyElement(
                "Chưa có sản phẩm"
            )
        );

        updateTotal();
        return;
    }

    cart.forEach((item, index) => {
        const row =
            document.createElement("div");

        row.className = "cart-item";

        const info =
            document.createElement("div");

        const name =
            document.createElement("strong");

        name.textContent = item.name;

        const detail =
            document.createElement("div");

        detail.className = "muted small";

        detail.textContent =
            item.itemType === "PLAN"
                ? "Gói tập"
                : `SL: ${item.quantity}`;

        info.append(name, detail);

        const actions =
            document.createElement("div");

        actions.className =
            "cart-item-controls";

        const total =
            document.createElement("strong");

        total.textContent =
            formatMoney(
                item.price
                * item.quantity
            );

        if (item.itemType === "PRODUCT") {
            const minus =
                smallControl("−");

            minus.addEventListener(
                "click",
                () => changeQuantity(
                    index,
                    -1
                )
            );

            const plus =
                smallControl("+");

            plus.addEventListener(
                "click",
                () => changeQuantity(
                    index,
                    1
                )
            );

            actions.append(
                minus,
                plus
            );
        }

        const remove =
            smallControl("×");

        remove.addEventListener(
            "click",
            () => {
                cart.splice(index, 1);
                renderCart();
            }
        );

        actions.append(
            total,
            remove
        );

        row.append(
            info,
            actions
        );

        container.appendChild(row);
    });

    updateTotal();
}

function changeQuantity(
    index,
    delta
) {
    const item = cart[index];

    const next =
        item.quantity + delta;

    if (next <= 0) {
        cart.splice(index, 1);
        renderCart();
        return;
    }

    if (
        next
        > productStock(item.productId)
    ) {
        showError(
            "Số lượng vượt tồn kho"
        );
        return;
    }

    item.quantity = next;
    renderCart();
}

function updateTotal() {
    const total = cart.reduce(
        (sum, item) =>
            sum
            + item.price
            * item.quantity,
        0
    );

    document.getElementById(
        "cart-total"
    ).textContent =
        formatMoney(total);
}

async function checkout() {
    if (!cart.length) {
        showError(
            "Giỏ hàng đang trống"
        );
        return;
    }

    const memberId =
        selectedMemberId();

    if (
        cart.some(
            item =>
                item.itemType === "PLAN"
        )
        && !memberId
    ) {
        showError(
            "Đơn mua gói tập cần hội viên"
        );
        return;
    }

    try {
        currentOrder = await Api.post(
            "/api/v1/orders",
            {
                branchId:
                currentUser.branchId,

                memberId,

                items: cart.map(
                    item => ({
                        itemType:
                        item.itemType,

                        planId:
                        item.planId,

                        productId:
                        item.productId,

                        quantity:
                        item.quantity
                    })
                )
            }
        );

        currentPayment =
            await Api.post(
                "/api/v1/payments/momo",
                {
                    orderId:
                    currentOrder.id,

                    idempotencyKey:
                        idempotencyKey(
                            currentOrder.id
                        )
                }
            );

        document.getElementById(
            "payment-order"
        ).textContent =
            currentOrder.orderCode;

        document.getElementById(
            "payment-amount"
        ).textContent =
            formatMoney(
                currentPayment.amount
            );

        hide("payment-error");
        show("payment-modal");
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

async function paymentSuccess() {
    try {
        await Api.post(
            `/api/v1/payments/${currentPayment.id}/simulate-success`,
            {}
        );

        hide("payment-modal");

        showSuccess(
            "Thanh toán thành công"
        );

        clearCart();

        await refreshAfterPayment();
    } catch (error) {
        showPaymentError(
            formatApiError(error)
        );
    }
}

async function paymentFailure() {
    try {
        await Api.post(
            `/api/v1/payments/${currentPayment.id}/simulate-failure`,
            {}
        );

        hide("payment-modal");

        showError(
            "Thanh toán thất bại"
        );

        clearCart();

        await loadOrders();
    } catch (error) {
        showPaymentError(
            formatApiError(error)
        );
    }
}

async function refreshAfterPayment() {
    inventory = await Api.get(
        "/api/v1/inventory"
    );

    await loadOrders();

    renderProducts();
}

async function loadOrders() {
    orders = await Api.get(
        "/api/v1/orders"
    );

    renderOrders();
}

function renderOrders() {
    const body =
        document.getElementById(
            "orders-body"
        );

    body.replaceChildren();

    if (!orders.length) {
        appendEmptyRow(
            body,
            5,
            "Chưa có đơn hàng"
        );
        return;
    }

    orders.slice(0, 20)
        .forEach(order => {
            const row =
                document.createElement("tr");

            appendCell(
                row,
                order.orderCode
            );

            appendCell(
                row,
                memberName(
                    order.memberId
                )
            );

            appendCell(
                row,
                formatMoney(
                    order.total
                )
            );

            appendCell(
                row,
                order.status
            );

            appendCell(
                row,
                formatDateTime(
                    order.createdAtUtc
                )
            );

            body.appendChild(row);
        });
}

function switchTab(type) {
    const planTab =
        document.getElementById(
            "plans-tab"
        );

    const productTab =
        document.getElementById(
            "products-tab"
        );

    if (type === "PLAN") {
        planTab.classList.add("active");
        productTab.classList.remove(
            "active"
        );

        show("plan-catalog");
        hide("product-catalog");
    } else {
        productTab.classList.add(
            "active"
        );

        planTab.classList.remove(
            "active"
        );

        show("product-catalog");
        hide("plan-catalog");
    }
}

function selectedMemberId() {
    const value =
        document.getElementById(
            "sales-member"
        ).value;

    return value
        ? Number(value)
        : null;
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

function productStock(productId) {
    const item = inventory.find(
        value =>
            value.productId === productId
    );

    return item
        ? Number(item.quantity)
        : 0;
}

function clearCart() {
    cart = [];
    renderCart();
}

function smallControl(text) {
    const button =
        document.createElement("button");

    button.type = "button";
    button.className =
        "cart-control";
    button.textContent = text;

    return button;
}

function emptyElement(text) {
    const div =
        document.createElement("div");

    div.className = "empty-state";
    div.textContent = text;

    return div;
}

function idempotencyKey(orderId) {
    if (window.crypto?.randomUUID) {
        return `manager-${orderId}-${crypto.randomUUID()}`;
    }

    return `manager-${orderId}-${Date.now()}`;
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

function showPaymentError(message) {
    const box =
        document.getElementById(
            "payment-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
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