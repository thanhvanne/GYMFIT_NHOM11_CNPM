const Invoice = {
    async download(order) {
        if (!order || !order.id) {
            throw new Error(
                "Đơn hàng không hợp lệ"
            );
        }

        const blob = await Api.blob(
            `/api/v1/orders/${order.id}/invoice`
        );

        const url =
            URL.createObjectURL(blob);

        const link =
            document.createElement("a");

        link.href = url;
        link.download =
            `GYMFIT-${order.orderCode}.pdf`;

        document.body.appendChild(link);

        link.click();
        link.remove();

        window.setTimeout(
            () => URL.revokeObjectURL(url),
            1000
        );
    },

    async open(order) {
        if (!order || !order.id) {
            throw new Error(
                "Đơn hàng không hợp lệ"
            );
        }

        const blob = await Api.blob(
            `/api/v1/orders/${order.id}/invoice`
        );

        const url =
            URL.createObjectURL(blob);

        const opened =
            window.open(
                url,
                "_blank",
                "noopener"
            );

        if (!opened) {
            URL.revokeObjectURL(url);

            throw new Error(
                "Trình duyệt đã chặn cửa sổ xem hóa đơn"
            );
        }

        window.setTimeout(
            () => URL.revokeObjectURL(url),
            60000
        );
    }
};

window.Invoice = Invoice;