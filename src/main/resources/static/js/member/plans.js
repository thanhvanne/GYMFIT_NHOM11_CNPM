let currentUser;
let member;
let branches = [];
let plans = [];
let currentOrder = null;
let purchasing = false;

document.addEventListener("DOMContentLoaded", async () => {
    currentUser = Auth.requireRole("MEMBER");

    if (!currentUser) return;

    try {
        // Tự nạp màn hình hóa đơn chung, không cần sửa HTML.
        if (!window.Invoice) {
            await import("/js/core/invoice.js");
        }

        bindEvents();

        const results = await Promise.all([
            Api.get(`/api/v1/members/${currentUser.memberId}`),
            Api.get("/api/v1/branches?status=ACTIVE")
        ]);

        member = results[0];
        branches = results[1];

        renderBranches();

        const defaultBranch = branches.find(
            branch => branch.id === member.homeBranchId
        ) || branches[0];

        if (defaultBranch) {
            document.getElementById("plan-branch").value =
                String(defaultBranch.id);

            await loadPlans();
        }

        await loadPurchaseHistory();
        await loadActiveMemberships();
    } catch (error) {
        showError(formatApiError(error));
    }
});

function bindEvents() {
    document.getElementById("plan-branch")
        .addEventListener("change", loadPlans);
}

function renderBranches() {
    const select = document.getElementById("plan-branch");

    select.replaceChildren();

    branches.forEach(branch => {
        const option = document.createElement("option");

        option.value = String(branch.id);
        option.textContent = branch.name;

        select.appendChild(option);
    });
}

async function loadPlans() {
    const branchId = document.getElementById("plan-branch").value;

    if (!branchId) return;

    try {
        plans = await Api.get(
            `/api/v1/plans?branchId=${branchId}&status=ACTIVE`
        );

        renderPlans();
    } catch (error) {
        showError(formatApiError(error));
    }
}

function renderPlans() {
    const container = document.getElementById("member-plan-grid");

    container.replaceChildren();

    if (!plans.length) {
        const empty = document.createElement("div");

        empty.className = "empty-state";
        empty.textContent = "Chi nhánh chưa có gói tập";

        container.appendChild(empty);
        return;
    }

    plans.forEach(plan => {
        const card = document.createElement("article");
        card.className = "manager-plan-card";

        const top = document.createElement("div");
        top.className = "manager-plan-top";

        const name = document.createElement("strong");
        name.textContent = plan.name;

        const tier = document.createElement("span");
        tier.className = tierClass(plan.tier);
        tier.textContent = plan.tier;

        top.append(name, tier);

        const code = document.createElement("div");
        code.className = "muted small";
        code.textContent = plan.planCode;

        const price = document.createElement("div");
        price.className = "manager-plan-price";
        price.textContent = formatMoney(plan.price);

        const duration = document.createElement("div");
        duration.className = "muted";
        duration.textContent = `${plan.durationDays} ngày`;

        const services = document.createElement("div");
        services.className = "service-badges";

        plan.services.forEach(service => {
            const badge = document.createElement("span");

            badge.className = "badge badge-standard";
            badge.textContent = service;

            services.appendChild(badge);
        });

        card.append(top, code, price, duration, services);

        if (plan.description) {
            const description = document.createElement("p");

            description.className = "muted small";
            description.textContent = plan.description;

            card.appendChild(description);
        }

        const purchase = document.createElement("button");

        purchase.type = "button";
        purchase.className = "button button-primary";
        purchase.textContent = "Mua gói";

        purchase.addEventListener(
            "click",
            () => purchasePlan(plan)
        );

        card.appendChild(purchase);
        container.appendChild(card);
    });
}

async function purchasePlan(plan) {
    if (purchasing) return;

    purchasing = true;

    document.querySelectorAll("#member-plan-grid button")
        .forEach(button => {
            button.disabled = true;
        });

    try {
        currentOrder = await Api.post(
            "/api/v1/member/purchases",
            {planId: plan.id}
        );

        await loadPurchaseHistory();

        const result = await Invoice.checkout(currentOrder);

        if (result.status === "PAID") {
            showSuccess(
                "Thanh toán thành công. Gói tập đã được kích hoạt."
            );
        }

        await loadPurchaseHistory();
        await loadActiveMemberships();
    } catch (error) {
        showError(formatApiError(error));
    } finally {
        purchasing = false;

        document.querySelectorAll("#member-plan-grid button")
            .forEach(button => {
                button.disabled = false;
            });
    }
}

async function loadPurchaseHistory() {
    let section = document.getElementById("purchase-history");

    if (!section) {
        section = document.createElement("section");
        section.id = "purchase-history";
        section.className = "panel member-section";

        document.querySelector(".member-main").appendChild(section);
    }

    const orders = await Api.get("/api/v1/orders");

    section.replaceChildren();

    const title = document.createElement("h2");
    title.textContent = "Đơn hàng / Hóa đơn";

    section.appendChild(title);

    orders.slice(0, 20).forEach(order => {
        const row = document.createElement("div");

        row.style.cssText = `
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            padding: 12px 0;
            flex-wrap: wrap;
        `;

        const text = document.createElement("span");

        text.textContent =
            `${order.orderCode} · ${formatMoney(order.total)} · `
            + (order.status === "PAID"
                ? "Đã thanh toán"
                : "Chưa thanh toán");

        row.appendChild(text);

        if (["PAID", "PENDING_PAYMENT"].includes(order.status)) {
            const button = document.createElement("button");

            button.type = "button";
            button.className = "button button-primary button-small";

            button.textContent = order.status === "PAID"
                ? "Hóa đơn / In"
                : "Tiếp tục thanh toán";

            button.addEventListener("click", async () => {
                button.disabled = true;

                try {
                    await Invoice.checkout(order);
                    await loadPurchaseHistory();
                } catch (error) {
                    showError(formatApiError(error));
                } finally {
                    button.disabled = false;
                }
            });

            row.appendChild(button);
        }

        section.appendChild(row);
    });

    if (!orders.length) {
        section.appendChild(
            document.createTextNode("Chưa có đơn hàng.")
        );
    }
}

async function loadActiveMemberships() {
    const section = document.getElementById("active-memberships");

    try {
        const memberships = await Api.get(
            `/api/v1/members/${currentUser.memberId}/memberships/active`
        );

        section.replaceChildren();

        if (!memberships.length) {
            section.classList.add("hidden");
            return;
        }

        section.classList.remove("hidden");

        const title = document.createElement("h2");
        title.textContent = "Các gói đang hoạt động";
        section.appendChild(title);

        const note = document.createElement("p");
        note.className = "muted small";
        note.textContent =
            "Mua thêm gói mới không thay thế các gói hiện tại.";
        section.appendChild(note);

        const list = document.createElement("div");
        list.className = "member-membership-list";

        const planDetails = await Promise.all(
            memberships.map(item =>
                Api.get(`/api/v1/plans/${item.planId}`)
            )
        );

        memberships.forEach((membership, index) => {
            const card = document.createElement("article");
            card.className = "active-plan-summary";

            const name = document.createElement("strong");
            name.textContent = planDetails[index]?.name
                || `Gói #${membership.planId}`;

            const dates = document.createElement("span");
            dates.className = "muted small";
            dates.textContent =
                `${formatPlanDate(membership.startDate)} – `
                + `${formatPlanDate(membership.endDate)}`;

            const services = document.createElement("span");
            services.className = "muted small";
            services.textContent = membership.services.join(" · ");

            card.append(name, dates, services);
            list.appendChild(card);
        });

        section.appendChild(list);
    } catch (error) {
        section.classList.add("hidden");
    }
}

function formatPlanDate(value) {
    if (!value) return "—";
    const [year, month, day] = value.split("-");
    return `${day}/${month}/${year}`;
}

function tierClass(tier) {
    if (tier === "PREMIUM") return "badge badge-premium";
    if (tier === "STANDARD") return "badge badge-standard";
    return "badge badge-basic";
}

function formatMoney(value) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND"
    }).format(Number(value || 0));
}

function show(id) {
    document.getElementById(id).classList.remove("hidden");
}

function hide(id) {
    document.getElementById(id).classList.add("hidden");
}

function showError(message) {
    const box = document.getElementById("page-error");

    box.textContent = message;
    box.classList.remove("hidden");
}

function showSuccess(message) {
    const box = document.getElementById("page-success");

    box.textContent = message;
    box.classList.remove("hidden");

    setTimeout(() => box.classList.add("hidden"), 4000);
}

function formatApiError(error) {
    if (error.errors && Object.keys(error.errors).length) {
        return Object.values(error.errors).join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}