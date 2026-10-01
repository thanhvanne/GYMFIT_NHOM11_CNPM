let currentUser;
let member;
let branches = [];
let plans = [];

let selectedPlan = null;
let currentOrder = null;
let currentPayment = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser =
            Auth.requireRole("MEMBER");

        if (!currentUser) {
            return;
        }

        bindEvents();

        try {
            const results =
                await Promise.all([
                    Api.get(
                        `/api/v1/members/${currentUser.memberId}`
                    ),
                    Api.get(
                        "/api/v1/branches?status=ACTIVE"
                    )
                ]);

            member = results[0];
            branches = results[1];

            renderBranches();

            const defaultBranch =
                branches.find(
                    branch =>
                        branch.id
                        === member.homeBranchId
                )
                || branches[0];

            if (defaultBranch) {
                document.getElementById(
                    "plan-branch"
                ).value =
                    String(
                        defaultBranch.id
                    );

                await loadPlans();
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
        "plan-branch"
    ).addEventListener(
        "change",
        loadPlans
    );

    document.getElementById(
        "purchase-success-button"
    ).addEventListener(
        "click",
        simulateSuccess
    );

    document.getElementById(
        "purchase-failure-button"
    ).addEventListener(
        "click",
        simulateFailure
    );
}

function renderBranches() {
    const select =
        document.getElementById(
            "plan-branch"
        );

    select.replaceChildren();

    branches.forEach(branch => {
        const option =
            document.createElement("option");

        option.value =
            String(branch.id);

        option.textContent =
            branch.name;

        select.appendChild(option);
    });
}

async function loadPlans() {
    const branchId =
        document.getElementById(
            "plan-branch"
        ).value;

    if (!branchId) {
        return;
    }

    try {
        plans = await Api.get(
            `/api/v1/plans`
            + `?branchId=${branchId}`
            + `&status=ACTIVE`
        );

        renderPlans();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function renderPlans() {
    const container =
        document.getElementById(
            "member-plan-grid"
        );

    container.replaceChildren();

    if (!plans.length) {
        const empty =
            document.createElement("div");

        empty.className =
            "empty-state";

        empty.textContent =
            "Chi nhánh chưa có gói tập";

        container.appendChild(empty);

        return;
    }

    plans.forEach(plan => {
        const card =
            document.createElement(
                "article"
            );

        card.className =
            "manager-plan-card";

        const top =
            document.createElement(
                "div"
            );

        top.className =
            "manager-plan-top";

        const name =
            document.createElement(
                "strong"
            );

        name.textContent =
            plan.name;

        const tier =
            document.createElement(
                "span"
            );

        tier.className =
            tierClass(plan.tier);

        tier.textContent =
            plan.tier;

        top.append(
            name,
            tier
        );

        const code =
            document.createElement(
                "div"
            );

        code.className =
            "muted small";

        code.textContent =
            plan.planCode;

        const price =
            document.createElement(
                "div"
            );

        price.className =
            "manager-plan-price";

        price.textContent =
            formatMoney(
                plan.price
            );

        const duration =
            document.createElement(
                "div"
            );

        duration.className =
            "muted";

        duration.textContent =
            `${plan.durationDays} ngày`;

        const services =
            document.createElement(
                "div"
            );

        services.className =
            "service-badges";

        plan.services.forEach(
            service => {
                const badge =
                    document.createElement(
                        "span"
                    );

                badge.className =
                    "badge badge-standard";

                badge.textContent =
                    service;

                services.appendChild(
                    badge
                );
            }
        );

        if (plan.description) {
            const description =
                document.createElement(
                    "p"
                );

            description.className =
                "muted small";

            description.textContent =
                plan.description;

            card.append(
                top,
                code,
                price,
                duration,
                services,
                description
            );
        } else {
            card.append(
                top,
                code,
                price,
                duration,
                services
            );
        }

        const purchase =
            document.createElement(
                "button"
            );

        purchase.type = "button";

        purchase.className =
            "button button-primary";

        purchase.textContent =
            "Mua gói";

        purchase.addEventListener(
            "click",
            () => purchasePlan(
                plan
            )
        );

        card.appendChild(
            purchase
        );

        container.appendChild(card);
    });
}

async function purchasePlan(plan) {
    selectedPlan = plan;

    hide("purchase-error");

    try {
        currentOrder = await Api.post(
            "/api/v1/member/purchases",
            {
                planId:
                plan.id
            }
        );

        currentPayment =
            await Api.post(
                "/api/v1/payments/momo",
                {
                    orderId:
                    currentOrder.id,

                    idempotencyKey:
                        createIdempotencyKey(
                            currentOrder.id
                        )
                }
            );

        document.getElementById(
            "purchase-plan-name"
        ).textContent =
            plan.name;

        document.getElementById(
            "purchase-amount"
        ).textContent =
            formatMoney(
                currentPayment.amount
            );

        document.getElementById(
            "purchase-order-code"
        ).textContent =
            currentOrder.orderCode;

        show("purchase-modal");
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

async function simulateSuccess() {
    if (!currentPayment) {
        return;
    }

    setPaymentButtons(true);

    try {
        const payment =
            await Api.post(
                `/api/v1/payments/${currentPayment.id}/simulate-success`,
                {}
            );

        if (
            payment.status
            !== "SUCCEEDED"
        ) {
            throw new Error(
                "Thanh toán chưa thành công"
            );
        }

        hide("purchase-modal");

        showSuccess(
            "Mua gói thành công. Membership mới đã được kích hoạt."
        );

        currentOrder = null;
        currentPayment = null;
        selectedPlan = null;
    } catch (error) {
        showPurchaseError(
            formatApiError(error)
        );
    } finally {
        setPaymentButtons(false);
    }
}

async function simulateFailure() {
    if (!currentPayment) {
        return;
    }

    setPaymentButtons(true);

    try {
        await Api.post(
            `/api/v1/payments/${currentPayment.id}/simulate-failure`,
            {}
        );

        hide("purchase-modal");

        showError(
            "Thanh toán thất bại"
        );

        currentOrder = null;
        currentPayment = null;
        selectedPlan = null;
    } catch (error) {
        showPurchaseError(
            formatApiError(error)
        );
    } finally {
        setPaymentButtons(false);
    }
}

function tierClass(tier) {
    if (tier === "PREMIUM") {
        return "badge badge-premium";
    }

    if (tier === "STANDARD") {
        return "badge badge-standard";
    }

    return "badge badge-basic";
}

function createIdempotencyKey(
    orderId
) {
    if (window.crypto?.randomUUID) {
        return `member-${orderId}-${crypto.randomUUID()}`;
    }

    return `member-${orderId}-${Date.now()}`;
}

function setPaymentButtons(
    disabled
) {
    document.getElementById(
        "purchase-success-button"
    ).disabled = disabled;

    document.getElementById(
        "purchase-failure-button"
    ).disabled = disabled;
}

function formatMoney(value) {
    return new Intl.NumberFormat(
        "vi-VN",
        {
            style: "currency",
            currency: "VND"
        }
    ).format(
        Number(value || 0)
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

    box.classList.remove(
        "hidden"
    );
}

function showSuccess(message) {
    const box =
        document.getElementById(
            "page-success"
        );

    box.textContent = message;

    box.classList.remove(
        "hidden"
    );

    setTimeout(
        () => box.classList.add(
            "hidden"
        ),
        4000
    );
}

function showPurchaseError(
    message
) {
    const box =
        document.getElementById(
            "purchase-error"
        );

    box.textContent = message;

    box.classList.remove(
        "hidden"
    );
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