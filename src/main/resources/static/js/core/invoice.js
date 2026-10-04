(() => {
    if (window.Invoice) return;

    const esc = value => String(value ?? "—").replace(
        /[&<>"']/g,
        c => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        }[c])
    );

    const money = value => new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND"
    }).format(Number(value || 0));

    const date = value => value
        ? new Intl.DateTimeFormat("vi-VN", {
            dateStyle: "short",
            timeStyle: "short",
            timeZone: "Asia/Ho_Chi_Minh"
        }).format(new Date(value))
        : "—";

    const css = `
        .receipt,
        .receipt * {
            box-sizing: border-box;
        }

        .receipt {
            font: 15px Arial, sans-serif;
            background: white;
            padding: 28px;
            line-height: 1.6;
            color: #172033;
        }

        .receipt h1 {
            font-size: 25px;
            margin: 0;
        }

        .receipt h2 {
            font-size: 20px;
            margin: 18px 0;
        }

        .receipt .muted {
            color: #64748b;
        }

        .receipt .meta {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
            margin: 18px 0;
        }

        .receipt table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }

        .receipt th,
        .receipt td {
            padding: 10px 6px;
            border-bottom: 1px solid #ddd;
            text-align: left;
        }

        .receipt .num {
            text-align: right;
        }

        .receipt .total {
            text-align: right;
            font-size: 22px;
            font-weight: bold;
            margin-top: 20px;
        }

        .receipt .status {
            padding: 8px 12px;
            background: #fff3cf;
            border-radius: 8px;
            display: inline-block;
        }

        .receipt .paid {
            background: #dcfce7;
            color: #166534;
        }

        .receipt .note {
            margin-top: 18px;
            font-size: 13px;
        }

        @media (max-width: 600px) {
            .receipt {
                padding: 14px;
            }

            .receipt .meta {
                grid-template-columns: 1fr;
            }

            .receipt table {
                font-size: 12px;
            }
        }

        @media print {
            @page {
                size: A4;
                margin: 14mm;
            }

            body {
                margin: 0;
            }

            .receipt {
                padding: 0;
            }

            tr {
                break-inside: avoid;
            }
        }
    `;

    let active = false;

    function receipt(order, branch, member, payment) {
        const paid = order.status === "PAID";

        return `
            <article class="receipt">
                <h1>GYMFIT</h1>
                <strong>${esc(branch.name)}</strong>

                <div class="muted">
                    ${esc(branch.address)} · ${esc(branch.phone)}
                </div>

                <h2>
                    ${paid ? "HÓA ĐƠN THANH TOÁN" : "PHIẾU TẠM TÍNH"}
                </h2>

                <span class="status ${paid ? "paid" : ""}">
                    ${paid ? "ĐÃ THANH TOÁN" : "CHƯA THANH TOÁN"}
                </span>

                <div class="meta">
                    <div>
                        Mã đơn: <b>${esc(order.orderCode)}</b><br>
                        Khách hàng: ${esc(member?.fullName || "Khách lẻ")}<br>
                        Mã hội viên: ${esc(member?.memberCode)}
                    </div>

                    <div>
                        Ngày lập: ${esc(date(order.createdAtUtc))}<br>
                        Ngày thanh toán: ${esc(date(order.paidAtUtc))}<br>

                        ${payment
            ? `Mã giao dịch: ${esc(payment.paymentCode)}
                               <br>MoMo mô phỏng`
            : ""}
                    </div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th>Nội dung</th>
                            <th class="num">SL</th>
                            <th class="num">Đơn giá</th>
                            <th class="num">Thành tiền</th>
                        </tr>
                    </thead>

                    <tbody>
                        ${order.items.map(item => `
                            <tr>
                                <td>${esc(item.name)}</td>
                                <td class="num">${esc(item.quantity)}</td>
                                <td class="num">${money(item.unitPrice)}</td>
                                <td class="num">${money(item.lineTotal)}</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>

                <div class="total">
                    Tổng cộng: ${money(order.total)}
                </div>

                ${!paid && order.items.some(item => item.itemType === "PLAN")
            ? `<p class="note">
                           Thanh toán thành công sẽ kích hoạt gói mới.
                           Các gói đang hoạt động khác vẫn được giữ nguyên.
                       </p>`
            : ""}

                <p class="note">
                    ${paid
            ? "Cảm ơn bạn đã sử dụng dịch vụ GYMFIT."
            : `Vui lòng kiểm tra thông tin trước khi thanh toán.
                           Phiếu này chưa xác nhận đã nhận tiền.`}
                </p>
            </article>
        `;
    }

    async function download(order) {
        const blob = await Api.blob(
            `/api/v1/orders/${order.id}/invoice`
        );

        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");

        link.href = url;
        link.download = `GYMFIT-${order.orderCode}.pdf`;

        document.body.appendChild(link);
        link.click();
        link.remove();

        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }

    async function open(input, payable = false) {
        if (active) {
            throw new Error("Hãy đóng hóa đơn đang mở trước.");
        }

        if (!input?.id) {
            throw new Error("Đơn hàng không hợp lệ");
        }

        active = true;

        let order;
        let branch;
        let member;

        try {
            order = await Api.get(
                `/api/v1/orders/${input.id}`
            );

            [branch, member] = await Promise.all([
                Api.get(`/api/v1/branches/${order.branchId}`),

                order.memberId
                    ? Api.get(`/api/v1/members/${order.memberId}`)
                    : null
            ]);
        } catch (error) {
            active = false;
            throw error;
        }

        return new Promise(resolve => {
            let payment = null;
            let qrUrl = null;
            let busy = false;
            let printFrame = null;

            const previousFocus = document.activeElement;
            const dialog = document.createElement("dialog");

            dialog.style.cssText = `
                width: min(1000px, 96vw);
                max-height: 94vh;
                padding: 0;
                border: 0;
                border-radius: 16px;
                background: #f1f5f9;
                color: #172033;
            `;

            dialog.innerHTML = `
                <style>
                    ${css}

                    dialog::backdrop {
                        background: #0f172a99;
                    }

                    .invoice-toolbar {
                        display: flex;
                        gap: 8px;
                        flex-wrap: wrap;
                        padding: 14px;
                        background: #e2e8f0;
                    }

                    .invoice-toolbar button,
                    .momo-box button {
                        padding: 10px 14px;
                        border: 0;
                        border-radius: 8px;
                        cursor: pointer;
                        background: #172033;
                        color: white;
                    }

                    .invoice-toolbar button:disabled,
                    .momo-box button:disabled {
                        opacity: .45;
                        cursor: wait;
                    }

                    .momo-box {
                        padding: 20px;
                        text-align: center;
                        border-top: 1px solid #ddd;
                        background: #fff5fa;
                    }

                    .momo-box img {
                        width: 230px;
                        max-width: 100%;
                        height: auto;
                        display: block;
                        margin: 12px auto;
                    }

                    .momo-box h3 {
                        color: #a50064;
                        margin: 0 0 10px;
                    }

                    .invoice-error {
                        color: #b91c1c;
                        padding: 12px 20px;
                        white-space: pre-wrap;
                    }
                </style>

                <div class="invoice-toolbar">
                    <button data-action="close">Đóng</button>
                    <button data-action="print">In phiếu</button>
                    <button data-action="pdf">Tải PDF</button>

                    <button data-action="refresh">
                        Kiểm tra trạng thái
                    </button>
                </div>

                <div class="invoice-error"
                     role="alert"
                     hidden></div>

                <div data-receipt></div>

                <section class="momo-box"></section>
            `;

            dialog.setAttribute(
                "aria-label",
                "Hóa đơn và thanh toán MoMo"
            );

            document.body.appendChild(dialog);

            const body = dialog.querySelector("[data-receipt]");
            const box = dialog.querySelector(".momo-box");
            const errorBox = dialog.querySelector(".invoice-error");

            const button = name => dialog.querySelector(
                `[data-action="${name}"]`
            );

            const storageKey = `gymfit-momo-${order.id}`;
            let key;

            try {
                key = sessionStorage.getItem(storageKey);
            } catch {
                // Vẫn sử dụng được khi trình duyệt chặn sessionStorage.
            }

            const newKey = () => {
                const random = globalThis.crypto?.randomUUID?.()
                    || Date.now();

                key = `invoice-${order.id}-${random}`;

                try {
                    sessionStorage.setItem(storageKey, key);
                } catch {
                    // Giữ key trong bộ nhớ của cửa sổ hiện tại.
                }
            };

            if (!key) newKey();

            function clearQr() {
                if (qrUrl) {
                    URL.revokeObjectURL(qrUrl);
                }

                qrUrl = null;
            }

            function render() {
                clearQr();

                body.innerHTML = receipt(
                    order,
                    branch,
                    member,
                    payment
                );

                button("print").textContent =
                    order.status === "PAID"
                        ? "In hóa đơn"
                        : "In phiếu tạm tính";

                button("pdf").hidden = order.status !== "PAID";

                box.hidden =
                    !payable
                    || order.status !== "PENDING_PAYMENT";

                if (box.hidden) return;

                box.innerHTML = `
                    <h3>Thanh toán MoMo — MÔ PHỎNG</h3>

                    <p>QR demo không dùng để chuyển tiền thật.</p>

                    <div data-qr></div>

                    <strong>${money(order.total)}</strong>

                    <p data-state></p>

                    <button data-action="success">
                        Mô phỏng thành công
                    </button>

                    <button data-action="failure">
                        Mô phỏng thất bại
                    </button>

                    <button data-action="retry">
                        Tạo lại giao dịch / QR
                    </button>
                `;

                const pending = payment?.status === "PENDING";

                button("success").hidden = !pending;
                button("failure").hidden = !pending;
                button("retry").hidden = pending;

                box.querySelector("[data-state]").textContent =
                    payment?.status === "FAILED"
                        ? "Giao dịch thất bại. Bạn có thể thử lại trên cùng đơn hàng."
                        : pending
                            ? `Đang chờ thanh toán · ${payment.paymentCode}`
                            : "Chưa tạo được giao dịch.";
            }

            async function loadQr() {
                if (box.hidden || payment?.status !== "PENDING") {
                    return;
                }

                const response = await fetch(
                    `/api/v1/payments/${payment.id}/qr`,
                    {
                        headers: {
                            Authorization: `Bearer ${Api.token()}`
                        },
                        cache: "no-store"
                    }
                );

                if (!response.ok) {
                    throw new Error(
                        "Không tải được QR. Bấm Kiểm tra trạng thái để thử lại."
                    );
                }

                clearQr();

                qrUrl = URL.createObjectURL(
                    await response.blob()
                );

                const img = document.createElement("img");

                img.alt =
                    "QR MoMo mô phỏng, không chuyển tiền thật";

                img.src = qrUrl;

                box.querySelector("[data-qr]")
                    .replaceChildren(img);
            }

            async function refresh() {
                order = await Api.get(
                    `/api/v1/orders/${order.id}`
                );

                if (payment) {
                    payment = await Api.get(
                        `/api/v1/payments/${payment.id}`
                    );
                }

                render();
                await loadQr();
            }

            async function createPayment() {
                payment = await Api.post(
                    "/api/v1/payments/momo",
                    {
                        orderId: order.id,
                        idempotencyKey: key
                    }
                );

                await refresh();
            }

            async function run(task) {
                if (busy) return;

                busy = true;
                errorBox.hidden = true;

                dialog.querySelectorAll("button").forEach(b => {
                    b.disabled = true;
                });

                try {
                    await task();
                } catch (error) {
                    errorBox.textContent =
                        error.message
                        || "Có lỗi xảy ra. Hãy thử lại.";

                    errorBox.hidden = false;
                } finally {
                    busy = false;

                    dialog.querySelectorAll("button").forEach(b => {
                        b.disabled = false;
                    });
                }
            }

            function print() {
                if (printFrame) {
                    printFrame.remove();
                }

                printFrame = document.createElement("iframe");

                printFrame.style.cssText = `
                    position: fixed;
                    width: 1px;
                    height: 1px;
                    left: -10000px;
                    border: 0;
                `;

                printFrame.title = "Bản in hóa đơn";
                document.body.appendChild(printFrame);

                const doc = printFrame.contentDocument;

                doc.open();

                doc.write(`
                    <!doctype html>
                    <html lang="vi">
                        <head>
                            <meta charset="UTF-8">
                            <title>GYMFIT-${esc(order.orderCode)}</title>
                            <style>${css}</style>
                        </head>

                        <body>
                            ${receipt(order, branch, member, payment)}
                        </body>
                    </html>
                `);

                doc.close();

                printFrame.contentWindow.focus();
                printFrame.contentWindow.print();
            }

            dialog.addEventListener("cancel", event => {
                if (busy) {
                    event.preventDefault();
                }
            });

            dialog.addEventListener("close", () => {
                clearQr();
                printFrame?.remove();
                dialog.remove();

                active = false;
                previousFocus?.focus();

                resolve(order);
            }, {once: true});

            dialog.addEventListener("click", event => {
                const action = event.target
                    .closest("button")
                    ?.dataset.action;

                if (!action || busy) return;

                if (action === "close") {
                    dialog.close();
                    return;
                }

                if (action === "print") {
                    print();
                    return;
                }

                run(async () => {
                    if (action === "pdf") {
                        await download(order);
                    }

                    if (action === "refresh") {
                        await refresh();
                    }

                    if (action === "retry") {
                        if (payment?.status === "FAILED") {
                            newKey();
                        }

                        await createPayment();
                    }

                    if (action === "success" || action === "failure") {
                        payment = await Api.post(
                            `/api/v1/payments/${payment.id}/simulate-${action}`,
                            {}
                        );

                        await refresh();
                    }
                });
            });

            render();
            dialog.showModal();

            if (payable && order.status === "PENDING_PAYMENT") {
                run(createPayment);
            }
        });
    }

    window.Invoice = {
        download,
        open: order => open(order),
        checkout: order => open(order, true)
    };
})();