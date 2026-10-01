let members = [];
let facilities = [];

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
            "manager-name"
        ).textContent =
            user.fullName;

        document.getElementById(
            "manager-avatar"
        ).textContent =
            initial(user.fullName);

        document.getElementById(
            "logout-button"
        ).addEventListener(
            "click",
            () => Auth.logout()
        );

        try {
            const results =
                await Promise.all([
                    Api.get(
                        "/api/v1/reports/dashboard"
                    ),

                    Api.get(
                        `/api/v1/branches/${user.branchId}`
                    ),

                    Api.get(
                        "/api/v1/members"
                    ),

                    Api.get(
                        "/api/v1/checkins"
                    ),

                    Api.get(
                        "/api/v1/bookings"
                    ),

                    Api.get(
                        "/api/v1/facilities"
                    )
                ]);

            const dashboard =
                results[0];

            const branch =
                results[1];

            members = results[2];

            const checkIns =
                results[3];

            const bookings =
                results[4];

            facilities =
                results[5];

            document.getElementById(
                "manager-branch-name"
            ).textContent =
                branch.name;

            renderStats(dashboard);

            renderRecentCheckIns(
                checkIns.slice(0, 8)
            );

            renderUpcomingBookings(
                bookings
            );
        } catch (error) {
            showError(
                error.message
                || "Không tải được dashboard"
            );
        }
    }
);

function renderStats(data) {
    document.getElementById(
        "stat-revenue"
    ).textContent =
        formatMoney(
            data.revenue
        );

    document.getElementById(
        "stat-checkins"
    ).textContent =
        formatNumber(
            data.checkIns
        );

    document.getElementById(
        "stat-bookings"
    ).textContent =
        formatNumber(
            data.bookings
        );

    document.getElementById(
        "stat-members"
    ).textContent =
        formatNumber(
            data.members
        );

    document.getElementById(
        "stat-memberships"
    ).textContent =
        formatNumber(
            data.activeMemberships
        );
}

function renderRecentCheckIns(items) {
    const body =
        document.getElementById(
            "recent-checkins"
        );

    body.replaceChildren();

    if (!items.length) {
        appendEmptyRow(
            body,
            4,
            "Chưa có check-in"
        );

        return;
    }

    items.forEach(item => {
        const row =
            document.createElement("tr");

        appendCell(
            row,
            formatDateTime(
                item.createdAtUtc
            )
        );

        appendCell(
            row,
            memberName(
                item.memberId
            )
        );

        appendCell(
            row,
            item.serviceCode
        );

        const resultCell =
            document.createElement("td");

        const badge =
            document.createElement("span");

        badge.className =
            item.result === "ACCEPTED"
                ? "badge badge-success"
                : "badge badge-danger";

        badge.textContent =
            item.result === "ACCEPTED"
                ? "Chấp nhận"
                : "Từ chối";

        resultCell.appendChild(
            badge
        );

        row.appendChild(
            resultCell
        );

        body.appendChild(row);
    });
}

function renderUpcomingBookings(
    bookings
) {
    const body =
        document.getElementById(
            "upcoming-bookings"
        );

    body.replaceChildren();

    const now = new Date();

    const upcoming = bookings
        .filter(
            booking =>
                booking.status
                === "CONFIRMED"
                && new Date(
                    booking.startsAtUtc
                ) > now
        )
        .sort(
            (a, b) =>
                new Date(
                    a.startsAtUtc
                )
                - new Date(
                    b.startsAtUtc
                )
        )
        .slice(0, 10);

    if (!upcoming.length) {
        appendEmptyRow(
            body,
            5,
            "Không có lịch sắp tới"
        );

        return;
    }

    upcoming.forEach(
        booking => {
            const row =
                document.createElement(
                    "tr"
                );

            appendCell(
                row,
                formatDateTime(
                    booking.startsAtUtc
                )
            );

            appendCell(
                row,
                memberName(
                    booking.memberId
                )
            );

            appendCell(
                row,
                booking.serviceCode
            );

            appendCell(
                row,
                facilityName(
                    booking.facilityId
                )
            );

            const statusCell =
                document.createElement(
                    "td"
                );

            const badge =
                document.createElement(
                    "span"
                );

            badge.className =
                "badge badge-success";

            badge.textContent =
                "Đã xác nhận";

            statusCell.appendChild(
                badge
            );

            row.appendChild(
                statusCell
            );

            body.appendChild(row);
        }
    );
}

function memberName(id) {
    if (id == null) {
        return "—";
    }

    const member =
        members.find(
            item => item.id === id
        );

    return member
        ? member.fullName
        : `#${id}`;
}

function facilityName(id) {
    const facility =
        facilities.find(
            item => item.id === id
        );

    return facility
        ? facility.name
        : `#${id}`;
}

function initial(value) {
    return String(value || "M")
        .trim()
        .charAt(0)
        .toUpperCase();
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

function formatNumber(value) {
    return new Intl.NumberFormat(
        "vi-VN"
    ).format(
        Number(value || 0)
    );
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
    cell.className =
        "empty-cell";

    cell.textContent =
        message;

    row.appendChild(cell);

    body.appendChild(row);
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