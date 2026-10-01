let currentUser;
let members = [];
let facilities = [];
let services = [];
let bookings = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser =
            Auth.requireRole(
                "BRANCH_MANAGER"
            );

        if (!currentUser) {
            return;
        }

        bindEvents();

        try {
            const [
                branch,
                memberData,
                serviceData,
                facilityData,
                bookingData
            ] = await Promise.all([
                Api.get(
                    `/api/v1/branches/${currentUser.branchId}`
                ),
                Api.get(
                    "/api/v1/members?status=ACTIVE"
                ),
                Api.get(
                    `/api/v1/branches/${currentUser.branchId}/services`
                ),
                Api.get(
                    "/api/v1/facilities"
                ),
                Api.get(
                    "/api/v1/bookings"
                )
            ]);

            members = memberData;
            services = serviceData;
            facilities = facilityData;
            bookings = bookingData;

            document.getElementById(
                "branch-name"
            ).textContent = branch.name;

            renderMemberOptions();
            renderServiceOptions();
            renderFacilities();
            renderBookings();
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
        "create-booking-button"
    ).addEventListener(
        "click",
        openBookingModal
    );

    document.getElementById(
        "booking-modal-close"
    ).addEventListener(
        "click",
        () => hide("booking-modal")
    );

    document.getElementById(
        "booking-service"
    ).addEventListener(
        "change",
        () => {
            renderFacilities();
            loadSlots();
        }
    );

    document.getElementById(
        "booking-facility"
    ).addEventListener(
        "change",
        loadSlots
    );

    document.getElementById(
        "booking-date"
    ).addEventListener(
        "change",
        loadSlots
    );

    document.getElementById(
        "booking-form"
    ).addEventListener(
        "submit",
        createBooking
    );

    document.getElementById(
        "booking-search"
    ).addEventListener(
        "input",
        renderBookings
    );

    document.getElementById(
        "status-filter"
    ).addEventListener(
        "change",
        renderBookings
    );

    document.getElementById(
        "cancel-modal-close"
    ).addEventListener(
        "click",
        () => hide("cancel-modal")
    );

    document.getElementById(
        "cancel-form"
    ).addEventListener(
        "submit",
        cancelBooking
    );
}

function renderMemberOptions() {
    const select = document.getElementById(
        "booking-member"
    );

    select.replaceChildren();

    members.forEach(member => {
        const option =
            document.createElement("option");

        option.value = member.id;
        option.textContent =
            `${member.memberCode} - ${member.fullName}`;

        select.appendChild(option);
    });
}

function renderServiceOptions() {
    const select = document.getElementById(
        "booking-service"
    );

    select.replaceChildren();

    services
        .filter(
            item =>
                item.bookingEnabled
        )
        .forEach(item => {
            const option =
                document.createElement("option");

            option.value =
                item.serviceCode;

            option.textContent =
                item.serviceCode;

            select.appendChild(option);
        });
}

function renderFacilities() {
    const select = document.getElementById(
        "booking-facility"
    );

    select.replaceChildren();

    const service =
        document.getElementById(
            "booking-service"
        ).value;

    facilities
        .filter(
            facility =>
                facility.status === "ACTIVE"
                && facility.serviceCode
                === service
        )
        .forEach(facility => {
            const option =
                document.createElement("option");

            option.value = facility.id;
            option.textContent =
                facility.name;

            select.appendChild(option);
        });
}

function openBookingModal() {
    const tomorrow = new Date();

    tomorrow.setDate(
        tomorrow.getDate() + 1
    );

    document.getElementById(
        "booking-date"
    ).value =
        localDate(tomorrow);

    hide("booking-form-error");
    show("booking-modal");

    loadSlots();
}

async function loadSlots() {
    const facilityId =
        document.getElementById(
            "booking-facility"
        ).value;

    const date =
        document.getElementById(
            "booking-date"
        ).value;

    const select =
        document.getElementById(
            "booking-slot"
        );

    select.replaceChildren();

    if (!facilityId || !date) {
        return;
    }

    try {
        const slots = await Api.get(
            `/api/v1/bookings/availability`
            + `?facilityId=${facilityId}`
            + `&date=${date}`
        );

        slots
            .filter(
                item =>
                    item.remainingCapacity > 0
            )
            .forEach(item => {
                const option =
                    document.createElement("option");

                option.value =
                    item.startsAtUtc;

                option.textContent =
                    `${formatTime(item.startsAtUtc)}`
                    + ` - ${formatTime(item.endsAtUtc)}`
                    + ` (${item.remainingCapacity} chỗ)`;

                select.appendChild(option);
            });
    } catch (error) {
        showBookingError(
            formatApiError(error)
        );
    }
}

async function createBooking(event) {
    event.preventDefault();

    const startsAt =
        document.getElementById(
            "booking-slot"
        ).value;

    if (!startsAt) {
        showBookingError(
            "Không có khung giờ được chọn"
        );
        return;
    }

    try {
        await Api.post(
            "/api/v1/bookings",
            {
                memberId: Number(
                    document.getElementById(
                        "booking-member"
                    ).value
                ),

                branchId:
                currentUser.branchId,

                serviceCode:
                document.getElementById(
                    "booking-service"
                ).value,

                facilityId: Number(
                    document.getElementById(
                        "booking-facility"
                    ).value
                ),

                startsAt
            }
        );

        hide("booking-modal");

        showSuccess(
            "Đã tạo booking"
        );

        await reloadBookings();
    } catch (error) {
        showBookingError(
            formatApiError(error)
        );
    }
}

async function reloadBookings() {
    bookings = await Api.get(
        "/api/v1/bookings"
    );

    renderBookings();
}

function renderBookings() {
    const body = document.getElementById(
        "booking-body"
    );

    body.replaceChildren();

    const search =
        document.getElementById(
            "booking-search"
        ).value.trim().toLowerCase();

    const status =
        document.getElementById(
            "status-filter"
        ).value;

    const filtered =
        bookings.filter(item => {
            const member = members.find(
                value =>
                    value.id === item.memberId
            );

            const matchSearch =
                !search
                || item.bookingCode
                    .toLowerCase()
                    .includes(search)
                || String(
                    member?.fullName || ""
                ).toLowerCase()
                    .includes(search)
                || String(
                    member?.memberCode || ""
                ).toLowerCase()
                    .includes(search);

            return matchSearch
                && (
                    !status
                    || item.status === status
                );
        });

    if (!filtered.length) {
        appendEmptyRow(
            body,
            7,
            "Không có booking"
        );
        return;
    }

    filtered.forEach(item => {
        const row =
            document.createElement("tr");

        appendCell(
            row,
            item.bookingCode
        );

        appendCell(
            row,
            memberName(item.memberId)
        );

        appendCell(
            row,
            item.serviceCode
        );

        appendCell(
            row,
            facilityName(item.facilityId)
        );

        appendCell(
            row,
            formatDateTime(
                item.startsAtUtc
            )
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            statusBadge(item.status)
        );

        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        if (item.status === "CONFIRMED") {
            const button =
                actionButton("Hủy");

            button.addEventListener(
                "click",
                () => openCancel(item)
            );

            actions.appendChild(button);
        }

        row.appendChild(actions);

        body.appendChild(row);
    });
}

function openCancel(item) {
    document.getElementById(
        "cancel-id"
    ).value = item.id;

    document.getElementById(
        "cancel-reason"
    ).value = "";

    show("cancel-modal");
}

async function cancelBooking(event) {
    event.preventDefault();

    const id = document.getElementById(
        "cancel-id"
    ).value;

    const reason =
        document.getElementById(
            "cancel-reason"
        ).value.trim();

    try {
        await Api.post(
            `/api/v1/bookings/${id}/cancel`,
            { reason }
        );

        hide("cancel-modal");

        showSuccess(
            "Đã hủy booking"
        );

        await reloadBookings();
    } catch (error) {
        showError(
            formatApiError(error)
        );
    }
}

function memberName(id) {
    const member = members.find(
        item => item.id === id
    );

    return member
        ? member.fullName
        : `#${id}`;
}

function facilityName(id) {
    const facility = facilities.find(
        item => item.id === id
    );

    return facility
        ? facility.name
        : `#${id}`;
}

function statusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "CONFIRMED") {
        badge.classList.add(
            "badge-success"
        );
    } else if (status === "CANCELLED") {
        badge.classList.add(
            "badge-danger"
        );
    } else {
        badge.classList.add(
            "badge-muted"
        );
    }

    badge.textContent = status;

    return badge;
}

function localDate(date) {
    return [
        date.getFullYear(),
        String(
            date.getMonth() + 1
        ).padStart(2, "0"),
        String(
            date.getDate()
        ).padStart(2, "0")
    ].join("-");
}

function formatTime(value) {
    return new Intl.DateTimeFormat(
        "vi-VN",
        {
            hour: "2-digit",
            minute: "2-digit",
            hour12: false,
            timeZone:
                "Asia/Ho_Chi_Minh"
        }
    ).format(new Date(value));
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

function showBookingError(message) {
    const box =
        document.getElementById(
            "booking-form-error"
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