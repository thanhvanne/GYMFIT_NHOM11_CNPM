let currentUser;
let expiryTime = 0;
let countdownTimer = null;
let refreshTimer = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser =
            Auth.requireRole("MEMBER");

        if (!currentUser) {
            return;
        }

        try {
            const member = await Api.get(
                `/api/v1/members/${currentUser.memberId}`
            );

            document.getElementById(
                "qr-member-name"
            ).textContent =
                member.fullName;

            document.getElementById(
                "qr-member-code"
            ).textContent =
                member.memberCode;

            try {
                const membership =
                    await Api.get(
                        `/api/v1/members/${currentUser.memberId}/memberships/current`
                    );

                document.getElementById(
                    "qr-membership"
                ).textContent =
                    `Membership #${membership.id}`
                    + ` · Hết hạn `
                    + formatDate(
                        membership.endDate
                    );
            } catch (error) {
                if (error.status !== 404) {
                    throw error;
                }

                document.getElementById(
                    "qr-membership"
                ).textContent =
                    "Chưa có membership đang hoạt động";
            }

            await refreshQr();
        } catch (error) {
            showError(
                error.message
                || "Không tải được QR"
            );
        }
    }
);

window.addEventListener(
    "beforeunload",
    () => {
        clearInterval(countdownTimer);
        clearTimeout(refreshTimer);
    }
);

async function refreshQr() {
    try {
        const response = await Api.post(
            "/api/v1/member/qr",
            {}
        );

        document.getElementById(
            "qr-image"
        ).src =
            response.imageDataUrl;

        expiryTime =
            new Date(
                response.expiresAtUtc
            ).getTime();

        startCountdown();

        clearTimeout(refreshTimer);

        const delay = Math.max(
            1000,
            expiryTime
            - Date.now()
            - 3000
        );

        refreshTimer =
            setTimeout(
                refreshQr,
                delay
            );
    } catch (error) {
        showError(
            error.message
            || "Không thể tạo QR"
        );
    }
}

function startCountdown() {
    clearInterval(countdownTimer);

    updateCountdown();

    countdownTimer =
        setInterval(
            updateCountdown,
            1000
        );
}

function updateCountdown() {
    const remaining =
        Math.max(
            0,
            Math.ceil(
                (expiryTime - Date.now())
                / 1000
            )
        );

    document.getElementById(
        "qr-countdown"
    ).textContent =
        String(remaining);
}

function formatDate(value) {
    if (!value) {
        return "—";
    }

    const [y, m, d] =
        value.split("-");

    return `${d}/${m}/${y}`;
}

function showError(message) {
    const box =
        document.getElementById(
            "page-error"
        );

    box.textContent = message;
    box.classList.remove("hidden");
}