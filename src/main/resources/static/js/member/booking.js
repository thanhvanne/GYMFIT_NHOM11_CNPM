let currentUser;
let memberships = [];
let branches = [];
let branch;
let selectedBranchId = null;
let facilities = [];
let bookings = [];

let selectedService = null;
let selectedFacility = null;
let selectedStart = null;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        currentUser = Auth.requireRole("MEMBER");

        if (!currentUser) return;

        bindEvents();

        try {
            const activeMemberships = await Api.get(
                `/api/v1/members/${currentUser.memberId}/memberships/active`
            );

            memberships = activeMemberships || [];
            bookings = await Api.get("/api/v1/bookings");

            const branchIds = [
                ...new Set(memberships.map(item => item.branchId))
            ];

            branches = await Promise.all(
                branchIds.map(id => Api.get(`/api/v1/branches/${id}`))
            );

            if (!memberships.length || !branches.length) {
                throw new Error("Bạn chưa có gói tập đang hoạt động");
            }

            renderBranches();
            setupDate();
            renderUpcoming();

            const requested = new URLSearchParams(location.search)
                .get("service");

            const preferredBranch = branches[0];

            selectedBranchId = preferredBranch.id;
            document.getElementById("branch-select").value =
                String(selectedBranchId);

            await selectBranch(selectedBranchId, requested);
        } catch (error) {
            showError(
                error.status === 404
                    ? "Bạn chưa có gói tập đang hoạt động"
                    : formatApiError(error)
            );
        }
    }
);

function bindEvents() {
    document.getElementById("branch-select")
        .addEventListener("change", async event => {
            await selectBranch(Number(event.target.value));
        });

    document.getElementById("facility-select")
        .addEventListener("change", async event => {
            selectedFacility = Number(event.target.value) || null;
            selectedStart = null;
            await loadSlots();
        });

    document.getElementById("booking-date")
        .addEventListener("change", async () => {
            selectedStart = null;
            await loadSlots();
        });

    document.getElementById("submit-booking")
        .addEventListener("click", createBooking);
}

async function selectBranch(branchId, requestedService = null) {
    selectedBranchId = Number(branchId) || null;
    branch = branches.find(item => item.id === selectedBranchId);
    selectedService = null;
    selectedFacility = null;
    selectedStart = null;

    const branchMemberships = membershipsForBranch();
    const services = [...new Set(
        branchMemberships.flatMap(item => item.services)
    )];

    document.getElementById("branch-name").textContent = branch
        ? `${branch.name} · Chỉ được đặt trong thời hạn và dịch vụ của gói`
        : "Chọn chi nhánh của gói tập";

    renderServices(services);

    const preferred = requestedService && services.includes(requestedService)
        ? requestedService
        : services[0];

    if (preferred) {
        await chooseService(preferred);
    } else {
        renderFacilities([]);
        renderNoSlots("Gói tập không có dịch vụ đặt lịch tại chi nhánh này");
    }
}

function renderBranches() {
    const select = document.getElementById("branch-select");
    select.replaceChildren();

    branches.forEach(item => {
        const option = document.createElement("option");
        option.value = String(item.id);
        option.textContent = item.name;
        select.appendChild(option);
    });
}

function membershipsForBranch() {
    return memberships.filter(item => item.branchId === selectedBranchId);
}

function renderServices(services) {
    const container = document.getElementById("service-selector");
    container.replaceChildren();

    if (!services.length) {
        container.appendChild(emptyElement("Không có loại hình thể thao phù hợp"));
        return;
    }

    services.forEach(service => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "member-service-button";
        button.dataset.service = service;
        button.textContent = serviceName(service);
        button.addEventListener("click", () => chooseService(service));
        container.appendChild(button);
    });
}

async function chooseService(service) {
    selectedService = service;
    selectedStart = null;

    document.querySelectorAll(".member-service-button").forEach(button => {
        button.classList.toggle("active", button.dataset.service === service);
    });

    updateDateBounds();

    try {
        facilities = await Api.get(
            `/api/v1/facilities`
            + `?branchId=${selectedBranchId}`
            + `&serviceCode=${service}`
            + "&status=ACTIVE"
        );

        renderFacilities(facilities);
        await loadSlots();
    } catch (error) {
        showError(formatApiError(error));
    }
}

function renderFacilities(items) {
    const select = document.getElementById("facility-select");
    select.replaceChildren();

    if (!items.length) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "Không có cơ sở vật chất";
        select.appendChild(option);
        selectedFacility = null;
        renderNoSlots("Không có cơ sở vật chất khả dụng");
        updateSubmit();
        return;
    }

    items.forEach(item => {
        const option = document.createElement("option");
        option.value = String(item.id);
        option.textContent = item.name;
        select.appendChild(option);
    });

    selectedFacility = items[0].id;
}

function setupDate() {
    const input = document.getElementById("booking-date");
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    input.min = dateValue(today);
    input.value = dateValue(tomorrow);
}

function updateDateBounds() {
    const input = document.getElementById("booking-date");
    const eligible = membershipsForBranch().filter(item =>
        item.services.includes(selectedService)
    );

    const maxDate = eligible
        .map(item => item.endDate)
        .sort()
        .at(-1);

    input.max = maxDate || "";

    if (maxDate && input.value > maxDate) {
        input.value = maxDate;
    }
}

async function loadSlots() {
    const date = document.getElementById("booking-date").value;
    selectedStart = null;
    updateSubmit();

    if (!selectedFacility || !date) {
        renderNoSlots("Chọn cơ sở vật chất và ngày");
        return;
    }

    try {
        const slots = await Api.get(
            `/api/v1/bookings/availability`
            + `?facilityId=${selectedFacility}`
            + `&date=${date}`
        );

        renderSlots(slots.filter(item => item.remainingCapacity > 0));
    } catch (error) {
        showError(formatApiError(error));
    }
}

function renderSlots(slots) {
    const container = document.getElementById("slot-grid");
    container.replaceChildren();

    if (!slots.length) {
        renderNoSlots("Không còn khung giờ trong thời hạn gói tập");
        return;
    }

    slots.forEach(slot => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "member-slot";
        button.textContent = formatTime(slot.startsAtUtc);
        button.title = `Còn ${slot.remainingCapacity} chỗ`;

        button.addEventListener("click", () => {
            selectedStart = slot.startsAtUtc;
            document.querySelectorAll(".member-slot").forEach(item =>
                item.classList.remove("active")
            );
            button.classList.add("active");
            updateSubmit();
        });

        container.appendChild(button);
    });
}

function renderNoSlots(message) {
    const container = document.getElementById("slot-grid");
    container.replaceChildren(emptyElement(message));
}

async function createBooking() {
    if (!selectedBranchId || !selectedService || !selectedFacility || !selectedStart) {
        return;
    }

    const button = document.getElementById("submit-booking");
    button.disabled = true;

    try {
        await Api.post("/api/v1/bookings", {
            branchId: selectedBranchId,
            serviceCode: selectedService,
            facilityId: selectedFacility,
            startsAt: selectedStart
        });

        showSuccess("Đặt lịch thành công");
        selectedStart = null;
        bookings = await Api.get("/api/v1/bookings");
        renderUpcoming();
        await loadSlots();
    } catch (error) {
        showError(formatApiError(error));
    } finally {
        updateSubmit();
    }
}

function renderUpcoming() {
    const container = document.getElementById("upcoming-bookings");
    container.replaceChildren();

    const now = new Date();
    const items = bookings
        .filter(item =>
            item.status === "CONFIRMED"
            && new Date(item.startsAtUtc) > now
        )
        .sort((a, b) => new Date(a.startsAtUtc) - new Date(b.startsAtUtc));

    if (!items.length) {
        container.appendChild(emptyElement("Chưa có lịch sắp tới"));
        return;
    }

    items.forEach(item => {
        const row = document.createElement("div");
        row.className = "member-list-item";

        const info = document.createElement("div");
        info.className = "member-list-main";

        const title = document.createElement("strong");
        title.textContent = serviceName(item.serviceCode);

        const time = document.createElement("span");
        time.className = "muted small";
        time.textContent = formatDateTime(item.startsAtUtc);
        info.append(title, time);

        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "member-cancel-button";
        cancel.textContent = "Hủy";
        cancel.addEventListener("click", () => cancelBooking(item));

        row.append(info, cancel);
        container.appendChild(row);
    });
}

async function cancelBooking(item) {
    const reason = window.prompt("Nhập lý do hủy:");
    if (reason === null) return;

    if (!reason.trim()) {
        showError("Lý do hủy không được để trống");
        return;
    }

    try {
        await Api.post(`/api/v1/bookings/${item.id}/cancel`, {
            reason: reason.trim()
        });

        showSuccess("Đã hủy booking");
        bookings = await Api.get("/api/v1/bookings");
        renderUpcoming();
        await loadSlots();
    } catch (error) {
        showError(formatApiError(error));
    }
}

function updateSubmit() {
    document.getElementById("submit-booking").disabled =
        !selectedBranchId
        || !selectedService
        || !selectedFacility
        || !selectedStart;
}

function serviceName(service) {
    return {
        GYM: "Gym",
        BOXING: "Boxing",
        PICKLEBALL: "Pickleball"
    }[service] || service;
}

function emptyElement(text) {
    const div = document.createElement("div");
    div.className = "empty-state";
    div.textContent = text;
    return div;
}

function dateValue(date) {
    return [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0")
    ].join("-");
}

function formatTime(value) {
    return new Intl.DateTimeFormat("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
        timeZone: branch?.timezone || "Asia/Ho_Chi_Minh"
    }).format(new Date(value));
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: branch?.timezone || "Asia/Ho_Chi_Minh"
    }).format(new Date(value));
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
    setTimeout(() => box.classList.add("hidden"), 3000);
}

function formatApiError(error) {
    if (error.errors && Object.keys(error.errors).length) {
        return Object.values(error.errors).join(". ");
    }
    return error.message || "Có lỗi xảy ra";
}
