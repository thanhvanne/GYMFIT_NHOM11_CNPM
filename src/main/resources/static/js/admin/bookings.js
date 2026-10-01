let bookings = [];
let branches = [];
let members = [];
let facilities = [];
let allFacilities = [];

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const user = Auth.requireRole("ADMIN");

        if (!user) {
            return;
        }

        bindEvents();

        await Promise.all([
            loadBranches(),
            loadMembers(),
            loadAllFacilities()
        ]);

        await loadBookings();
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
        openCreateBooking
    );

    document.getElementById(
        "booking-modal-close"
    ).addEventListener(
        "click",
        closeBookingModal
    );

    document.getElementById(
        "booking-cancel-form-button"
    ).addEventListener(
        "click",
        closeBookingModal
    );

    document.getElementById(
        "booking-form"
    ).addEventListener(
        "submit",
        createBooking
    );

    document.getElementById(
        "booking-branch"
    ).addEventListener(
        "change",
        loadBookingServices
    );

    document.getElementById(
        "booking-service"
    ).addEventListener(
        "change",
        loadBookingFacilities
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
        "booking-search"
    ).addEventListener(
        "input",
        renderBookings
    );

    document.getElementById(
        "booking-branch-filter"
    ).addEventListener(
        "change",
        renderBookings
    );

    document.getElementById(
        "booking-status-filter"
    ).addEventListener(
        "change",
        renderBookings
    );

    document.getElementById(
        "cancel-booking-close"
    ).addEventListener(
        "click",
        () => hide("cancel-booking-modal")
    );

    document.getElementById(
        "cancel-booking-form"
    ).addEventListener(
        "submit",
        cancelBooking
    );
}

async function loadBranches() {
    branches = await Api.get(
        "/api/v1/branches"
    );

    const filter = document.getElementById(
        "booking-branch-filter"
    );

    const form = document.getElementById(
        "booking-branch"
    );

    filter.replaceChildren();
    form.replaceChildren();

    const all =
        document.createElement("option");

    all.value = "";
    all.textContent = "Tất cả chi nhánh";
    filter.appendChild(all);

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent = "Chọn chi nhánh";
    form.appendChild(placeholder);

    branches.forEach(branch => {
        const filterOption =
            document.createElement("option");

        filterOption.value = branch.id;
        filterOption.textContent = branch.name;
        filter.appendChild(filterOption);

        if (branch.status === "ACTIVE") {
            const formOption =
                document.createElement("option");

            formOption.value = branch.id;
            formOption.textContent = branch.name;
            form.appendChild(formOption);
        }
    });
}

async function loadMembers() {
    members = await Api.get(
        "/api/v1/members?status=ACTIVE"
    );

    const select = document.getElementById(
        "booking-member"
    );

    select.replaceChildren();

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent = "Chọn hội viên";
    select.appendChild(placeholder);

    members.forEach(member => {
        const option =
            document.createElement("option");

        option.value = member.id;
        option.textContent =
            `${member.memberCode} - ${member.fullName}`;

        select.appendChild(option);
    });
}

async function loadBookings() {
    try {
        bookings = await Api.get(
            "/api/v1/bookings"
        );

        renderBookings();
    } catch (error) {
        showPageError(error.message);
    }
}

function renderBookings() {
    const body = document.getElementById(
        "booking-table-body"
    );

    body.replaceChildren();

    const search = document.getElementById(
        "booking-search"
    ).value.trim().toLowerCase();

    const branchFilter =
        document.getElementById(
            "booking-branch-filter"
        ).value;

    const statusFilter =
        document.getElementById(
            "booking-status-filter"
        ).value;

    const filtered = bookings.filter(item => {
        const member = members.find(
            value => value.id === item.memberId
        );

        const searchMatch =
            !search
            || item.bookingCode
                .toLowerCase()
                .includes(search)
            || String(
                member?.fullName || ""
            ).toLowerCase().includes(search)
            || String(
                member?.memberCode || ""
            ).toLowerCase().includes(search);

        const branchMatch =
            !branchFilter
            || item.branchId
            === Number(branchFilter);

        const statusMatch =
            !statusFilter
            || item.status === statusFilter;

        return searchMatch
            && branchMatch
            && statusMatch;
    });

    if (!filtered.length) {
        appendEmptyRow(
            body,
            8,
            "Không có booking"
        );
        return;
    }

    filtered.forEach(item => {
        const row =
            document.createElement("tr");

        appendCell(row, item.bookingCode);
        appendCell(
            row,
            memberName(item.memberId)
        );
        appendCell(
            row,
            branchName(item.branchId)
        );
        appendCell(row, item.serviceCode);
        appendCell(
            row,
            facilityName(item.facilityId)
        );

        appendCell(
            row,
            `${formatDateTime(item.startsAtUtc)}
             → ${formatTime(item.endsAtUtc)}`
        );

        const statusCell =
            document.createElement("td");

        statusCell.appendChild(
            bookingStatusBadge(item.status)
        );

        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        if (item.status === "CONFIRMED") {
            const cancel =
                actionButton("Hủy");

            cancel.addEventListener(
                "click",
                () => openCancelBooking(item)
            );

            actions.appendChild(cancel);
        }

        row.appendChild(actions);
        body.appendChild(row);
    });
}

async function openCreateBooking() {
    document.getElementById(
        "booking-form"
    ).reset();

    document.getElementById(
        "booking-service"
    ).replaceChildren();

    document.getElementById(
        "booking-facility"
    ).replaceChildren();

    document.getElementById(
        "booking-slot"
    ).replaceChildren();

    const tomorrow = new Date();
    tomorrow.setDate(
        tomorrow.getDate() + 1
    );

    document.getElementById(
        "booking-date"
    ).value = localDateValue(tomorrow);

    hide("booking-form-error");
    show("booking-modal");
}

async function loadBookingServices() {
    const branchId = Number(
        document.getElementById(
            "booking-branch"
        ).value
    );

    const select = document.getElementById(
        "booking-service"
    );

    select.replaceChildren();

    document.getElementById(
        "booking-facility"
    ).replaceChildren();

    document.getElementById(
        "booking-slot"
    ).replaceChildren();

    if (!branchId) {
        return;
    }

    try {
        const services = await Api.get(
            `/api/v1/branches/${branchId}/services`
        );

        services
            .filter(service =>
                service.bookingEnabled
            )
            .forEach(service => {
                const option =
                    document.createElement("option");

                option.value =
                    service.serviceCode;

                option.textContent =
                    service.serviceCode;

                select.appendChild(option);
            });

        await loadBookingFacilities();
    } catch (error) {
        showBookingFormError(
            error.message
        );
    }
}

async function loadBookingFacilities() {
    const branchId = Number(
        document.getElementById(
            "booking-branch"
        ).value
    );

    const serviceCode =
        document.getElementById(
            "booking-service"
        ).value;

    const select =
        document.getElementById(
            "booking-facility"
        );

    select.replaceChildren();

    if (!branchId || !serviceCode) {
        return;
    }

    try {
        facilities = await Api.get(
            `/api/v1/facilities?branchId=${branchId}`
            + `&serviceCode=${serviceCode}`
            + `&status=ACTIVE`
        );

        facilities.forEach(facility => {
            const option =
                document.createElement("option");

            option.value = facility.id;
            option.textContent = facility.name;

            select.appendChild(option);
        });

        await loadSlots();
    } catch (error) {
        showBookingFormError(
            error.message
        );
    }
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
            .filter(slot =>
                slot.remainingCapacity > 0
            )
            .forEach(slot => {
                const option =
                    document.createElement("option");

                option.value =
                    slot.startsAtUtc;

                option.textContent =
                    `${formatTime(slot.startsAtUtc)}`
                    + ` - ${formatTime(slot.endsAtUtc)}`
                    + ` (${slot.remainingCapacity} chỗ)`;

                select.appendChild(option);
            });

        if (!select.options.length) {
            const option =
                document.createElement("option");

            option.value = "";
            option.textContent =
                "Không còn khung giờ";

            select.appendChild(option);
        }
    } catch (error) {
        showBookingFormError(
            error.message
        );
    }
}

async function createBooking(event) {
    event.preventDefault();

    const data = {
        memberId: Number(
            document.getElementById(
                "booking-member"
            ).value
        ),

        branchId: Number(
            document.getElementById(
                "booking-branch"
            ).value
        ),

        serviceCode:
        document.getElementById(
            "booking-service"
        ).value,

        facilityId: Number(
            document.getElementById(
                "booking-facility"
            ).value
        ),

        startsAt:
        document.getElementById(
            "booking-slot"
        ).value
    };

    if (!data.startsAt) {
        showBookingFormError(
            "Vui lòng chọn khung giờ"
        );
        return;
    }

    try {
        await Api.post(
            "/api/v1/bookings",
            data
        );

        hide("booking-modal");

        showSuccess(
            "Đã tạo booking"
        );

        await loadBookings();
    } catch (error) {
        showBookingFormError(
            formatApiError(error)
        );
    }
}

function openCancelBooking(booking) {
    document.getElementById(
        "cancel-booking-id"
    ).value = booking.id;

    document.getElementById(
        "cancel-booking-reason"
    ).value = "";

    hide("cancel-booking-error");
    show("cancel-booking-modal");
}

async function cancelBooking(event) {
    event.preventDefault();

    const id = document.getElementById(
        "cancel-booking-id"
    ).value;

    const reason = document.getElementById(
        "cancel-booking-reason"
    ).value.trim();

    try {
        await Api.post(
            `/api/v1/bookings/${id}/cancel`,
            { reason }
        );

        hide("cancel-booking-modal");

        showSuccess(
            "Đã hủy booking"
        );

        await loadBookings();
    } catch (error) {
        const box = document.getElementById(
            "cancel-booking-error"
        );

        box.textContent =
            formatApiError(error);

        box.classList.remove("hidden");
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

function branchName(id) {
    const branch = branches.find(
        item => item.id === id
    );

    return branch
        ? branch.name
        : `#${id}`;
}

function facilityName(id) {
    const facility = allFacilities.find(
        item => item.id === id
    );

    return facility
        ? facility.name
        : `#${id}`;
}

function bookingStatusBadge(status) {
    const badge =
        document.createElement("span");

    badge.className = "badge";

    if (status === "CONFIRMED") {
        badge.classList.add(
            "badge-success"
        );
        badge.textContent = "Đã xác nhận";
    } else if (status === "COMPLETED") {
        badge.classList.add(
            "badge-muted"
        );
        badge.textContent = "Hoàn thành";
    } else {
        badge.classList.add(
            "badge-danger"
        );
        badge.textContent = "Đã hủy";
    }

    return badge;
}

function localDateValue(date) {
    const year = date.getFullYear();
    const month = String(
        date.getMonth() + 1
    ).padStart(2, "0");
    const day = String(
        date.getDate()
    ).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat(
        "vi-VN",
        {
            dateStyle: "short",
            timeStyle: "short",
            timeZone: "Asia/Ho_Chi_Minh"
        }
    ).format(new Date(value));
}

function formatTime(value) {
    return new Intl.DateTimeFormat(
        "vi-VN",
        {
            hour: "2-digit",
            minute: "2-digit",
            hour12: false,
            timeZone: "Asia/Ho_Chi_Minh"
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

function closeBookingModal() {
    hide("booking-modal");
}

function show(id) {
    document.getElementById(id)
        .classList.remove("hidden");
}

function hide(id) {
    document.getElementById(id)
        .classList.add("hidden");
}

function showPageError(message) {
    const box = document.getElementById(
        "page-error"
    );

    box.textContent = message;
    box.classList.remove("hidden");
}

function showBookingFormError(message) {
    const box = document.getElementById(
        "booking-form-error"
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

function formatApiError(error) {
    if (
        error.errors
        && Object.keys(error.errors).length
    ) {
        return Object.values(
            error.errors
        ).join(". ");
    }

    return error.message || "Có lỗi xảy ra";
}

async function loadAllFacilities() {
    allFacilities = await Api.get(
        "/api/v1/facilities"
    );
}