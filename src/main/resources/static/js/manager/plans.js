let plans = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user =
            Auth.requireRole(
                "BRANCH_MANAGER"
            );

        if (!user) {
            return;
        }

        document.getElementById(
            "logout-button"
        ).addEventListener(
            "click",
            () => Auth.logout()
        );

        document.getElementById(
            "plan-search"
        ).addEventListener(
            "input",
            renderPlans
        );

        document.getElementById(
            "plan-status"
        ).addEventListener(
            "change",
            loadPlans
        );

        await loadPlans();
    }
);

async function loadPlans() {
    const status =
        document.getElementById(
            "plan-status"
        ).value;

    try {
        plans = await Api.get(
            status
                ? `/api/v1/plans?status=${status}`
                : "/api/v1/plans"
        );

        renderPlans();
    } catch (error) {
        showError(error.message);
    }
}

function renderPlans() {
    const grid =
        document.getElementById(
            "plan-grid"
        );

    grid.replaceChildren();

    const query =
        document.getElementById(
            "plan-search"
        ).value
            .trim()
            .toLowerCase();

    const filtered = plans.filter(
        plan =>
            !query
            || plan.name
                .toLowerCase()
                .includes(query)
            || plan.planCode
                .toLowerCase()
                .includes(query)
    );

    if (!filtered.length) {
        const empty =
            document.createElement("div");

        empty.className =
            "empty-state";

        empty.textContent =
            "Không có gói tập";

        grid.appendChild(empty);
        return;
    }

    filtered.forEach(plan => {
        const card =
            document.createElement("article");

        card.className =
            "manager-plan-card";

        const top =
            document.createElement("div");

        top.className =
            "manager-plan-top";

        const name =
            document.createElement("strong");

        name.textContent =
            plan.name;

        const tier =
            document.createElement("span");

        tier.className =
            "badge badge-yellow";

        tier.textContent =
            plan.tier;

        top.append(name, tier);

        const code =
            document.createElement("div");

        code.className =
            "muted small";

        code.textContent =
            plan.planCode;

        const price =
            document.createElement("div");

        price.className =
            "manager-plan-price";

        price.textContent =
            formatMoney(plan.price);

        const duration =
            document.createElement("div");

        duration.className =
            "muted";

        duration.textContent =
            `${plan.durationDays} ngày`;

        const services =
            document.createElement("div");

        services.className =
            "service-badges";

        plan.services.forEach(service => {
            const badge =
                document.createElement("span");

            badge.className =
                "badge badge-standard";

            badge.textContent =
                service;

            services.appendChild(badge);
        });

        const status =
            document.createElement("span");

        status.className =
            plan.status === "ACTIVE"
                ? "badge badge-success"
                : "badge badge-muted";

        status.textContent =
            plan.status === "ACTIVE"
                ? "Hoạt động"
                : "Ngừng hoạt động";

        card.append(
            top,
            code,
            price,
            duration,
            services,
            status
        );

        grid.appendChild(card);
    });
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

function showError(message) {
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}