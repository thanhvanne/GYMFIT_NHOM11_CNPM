/**
 * Trang quản trị chatbot (V2-1):
 * xem thống kê, gán nhãn câu hỏi bot trả lời chưa chắc chắn, export JSONL.
 *
 * Quy tắc: chỉ dùng textContent/DOM API, KHÔNG innerHTML.
 */

let candidateItems = [];
let intentOptions = [];
let currentPage = 0;
const pageSize = 20;
let totalPages = 1;

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        const currentUser =
            Auth.requireRole("ADMIN");

        if (!currentUser) {
            return;
        }

        bindEvents();

        try {
            intentOptions = await Api.get(
                "/api/v1/chatbot/intents"
            );

            await Promise.all([
                loadStats(),
                loadCandidates()
            ]);
        } catch (error) {
            showPageError(
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
        "candidate-status-filter"
    ).addEventListener(
        "change",
        () => {
            currentPage = 0;
            loadCandidates();
        }
    );

    document.getElementById(
        "refresh-button"
    ).addEventListener(
        "click",
        refreshAll
    );

    document.getElementById(
        "export-button"
    ).addEventListener(
        "click",
        exportJsonl
    );

    document.getElementById(
        "prev-page"
    ).addEventListener(
        "click",
        () => {
            if (currentPage > 0) {
                currentPage--;
                loadCandidates();
            }
        }
    );

    document.getElementById(
        "next-page"
    ).addEventListener(
        "click",
        () => {
            if (currentPage < totalPages - 1) {
                currentPage++;
                loadCandidates();
            }
        }
    );
}

async function refreshAll() {
    try {
        await Promise.all([
            loadStats(),
            loadCandidates()
        ]);

        showSuccess("Đã tải lại dữ liệu");
    } catch (error) {
        showPageError(
            formatApiError(error)
        );
    }
}

// ------------------------------------------------------------------
// Thống kê
// ------------------------------------------------------------------

async function loadStats() {
    const stats = await Api.get(
        "/api/v1/chatbot/stats"
    );

    document.getElementById(
        "stat-messages"
    ).textContent =
        String(stats.messagesLast24h);

    document.getElementById(
        "stat-fallback"
    ).textContent =
        percent(stats.fallbackRate);

    document.getElementById(
        "stat-pending"
    ).textContent =
        String(stats.candidatesPending);

    document.getElementById(
        "stat-labeled"
    ).textContent =
        stats.candidatesLabeled
        + " / "
        + stats.candidatesRejected;

    renderFeedback(stats.feedbackByIntent || []);
}

function percent(value) {
    const ratio = Number(value) || 0;

    return (ratio * 100).toFixed(1) + "%";
}

function renderFeedback(rows) {
    const body = document.getElementById(
        "feedback-table-body"
    );

    body.replaceChildren();

    if (!rows.length) {
        appendEmptyRow(
            body,
            4,
            "Chưa có phản hồi 👍/👎"
        );
        return;
    }

    rows.forEach(row => {
        const tr =
            document.createElement("tr");

        appendCell(tr, row.intent);
        appendCell(tr, String(row.up));
        appendCell(tr, String(row.down));
        appendCell(tr, percent(row.downRate));

        body.appendChild(tr);
    });
}

// ------------------------------------------------------------------
// Danh sách câu hỏi
// ------------------------------------------------------------------

async function loadCandidates() {
    const status = document.getElementById(
        "candidate-status-filter"
    ).value;

    const query = new URLSearchParams({
        page: String(currentPage),
        size: String(pageSize)
    });

    if (status) {
        query.set("status", status);
    }

    try {
        const data = await Api.get(
            "/api/v1/chatbot/candidates?" + query
        );

        candidateItems = data.items || [];

        totalPages = Math.max(
            1,
            Math.ceil(data.total / pageSize)
        );

        renderCandidates();
        renderPager(data.total);
    } catch (error) {
        showPageError(
            formatApiError(error)
        );
    }
}

function renderCandidates() {
    const body = document.getElementById(
        "candidate-table-body"
    );

    body.replaceChildren();

    if (!candidateItems.length) {
        appendEmptyRow(
            body,
            7,
            "Không có câu hỏi nào"
        );
        return;
    }

    candidateItems.forEach(candidate => {
        const row =
            document.createElement("tr");

        appendCell(row, candidate.text);
        appendCell(row, candidate.predictedIntent);

        appendCell(
            row,
            candidate.confidence == null
                ? "—"
                : Number(candidate.confidence)
                    .toFixed(2)
        );

        // Ô chọn nhãn
        const labelCell =
            document.createElement("td");

        labelCell.appendChild(
            labelSelect(candidate)
        );

        row.appendChild(labelCell);

        // Trạng thái
        const statusCell =
            document.createElement("td");

        const badge =
            document.createElement("span");

        badge.className = "badge";
        badge.textContent =
            statusText(candidate.status);

        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        appendCell(
            row,
            candidate.createdAtUtc
                ? formatDateTime(
                    candidate.createdAtUtc
                )
                : "—"
        );

        // Hành động
        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        const labelButton =
            actionButton("Gán nhãn");

        labelButton.addEventListener(
            "click",
            () => labelCandidate(candidate, "LABELED")
        );

        const rejectButton =
            actionButton("Loại bỏ");

        rejectButton.addEventListener(
            "click",
            () => labelCandidate(candidate, "REJECTED")
        );

        const resetButton =
            actionButton("Chờ");

        resetButton.addEventListener(
            "click",
            () => labelCandidate(candidate, "PENDING")
        );

        actions.append(
            labelButton,
            rejectButton,
            resetButton
        );

        row.appendChild(actions);

        body.appendChild(row);
    });
}

function labelSelect(candidate) {
    const select =
        document.createElement("select");

    select.id = "label-" + candidate.id;
    select.dataset.candidateId =
        String(candidate.id);

    const placeholder =
        document.createElement("option");

    placeholder.value = "";
    placeholder.textContent = "— Chọn nhãn —";

    select.appendChild(placeholder);

    intentOptions.forEach(option => {
        const element =
            document.createElement("option");

        element.value = option.name;
        element.textContent =
            option.name + " · " + option.desc;

        select.appendChild(element);
    });

    if (candidate.label) {
        select.value = candidate.label;
    }

    return select;
}

async function labelCandidate(candidate, status) {
    const select =
        document.getElementById(
            "label-" + candidate.id
        );

    const label =
        select ? select.value : "";

    if (status === "LABELED" && !label) {
        showPageError(
            "Phải chọn nhãn intent trước khi gán nhãn"
        );
        return;
    }

    try {
        await Api.put(
            "/api/v1/chatbot/candidates/" + candidate.id,
            {
                label: label || null,
                status
            }
        );

        showSuccess(
            status === "LABELED"
                ? "Đã gán nhãn " + label
                : status === "REJECTED"
                    ? "Đã loại câu hỏi này"
                    : "Đã đưa về hàng chờ"
        );

        await Promise.all([
            loadStats(),
            loadCandidates()
        ]);
    } catch (error) {
        showPageError(
            formatApiError(error)
        );
    }
}

function renderPager(total) {
    document.getElementById(
        "page-info"
    ).textContent =
        "Trang "
        + (currentPage + 1)
        + "/"
        + totalPages
        + " · "
        + total
        + " câu hỏi";

    document.getElementById(
        "prev-page"
    ).disabled = currentPage <= 0;

    document.getElementById(
        "next-page"
    ).disabled =
        currentPage >= totalPages - 1;
}

// ------------------------------------------------------------------
// Export
// ------------------------------------------------------------------

async function exportJsonl() {
    try {
        const headers = new Headers();

        headers.set(
            "Accept",
            "application/jsonl"
        );

        const token = Api.token();

        if (token) {
            headers.set(
                "Authorization",
                `Bearer ${token}`
            );
        }

        const response = await fetch(
            "/api/v1/chatbot/export",
            {
                method: "GET",
                headers
            }
        );

        if (!response.ok) {
            showPageError(
                "Không tải được file export"
            );
            return;
        }

        const blob = await response.blob();

        const url =
            URL.createObjectURL(blob);

        const link =
            document.createElement("a");

        link.href = url;
        link.download = "seed_from_logs.jsonl";

        document.body.appendChild(link);
        link.click();
        link.remove();

        URL.revokeObjectURL(url);

        showSuccess(
            "Đã tải file – dán vào chatbot/seed_from_logs.jsonl rồi huấn luyện lại"
        );
    } catch (error) {
        showPageError(
            formatApiError(error)
        );
    }
}

// ------------------------------------------------------------------
// Tiện ích (bản riêng cho trang này, không có file dùng chung)
// ------------------------------------------------------------------

function statusText(status) {
    switch (status) {
        case "LABELED":
            return "Đã gán";
        case "REJECTED":
            return "Bị loại";
        default:
            return "Chờ gán";
    }
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

function showSuccess(message) {
    const box = document.getElementById(
        "page-success"
    );

    box.textContent = message;
    box.classList.remove("hidden");

    setTimeout(
        () => box.classList.add("hidden"),
        4000
    );
}

function showPageError(message) {
    const box = document.getElementById(
        "page-error"
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
