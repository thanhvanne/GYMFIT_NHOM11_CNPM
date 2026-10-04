# NOTES – Luồng xuất hóa đơn (tải về PDF)

> Phạm vi: chứng minh + bọc test cho luồng "xuất hóa đơn tải về dạng PDF".
> Điều kiện: **không thêm dependency mới** (dependency `openhtmltopdf` đã có sẵn từ trước).

## 1. Hiện trạng khi bắt tay vào

Luồng **đã tồn tại** trong code nhưng commit ghi *"chưa xong"*, chưa hề có test:

| Thành phần | File | Ghi chú |
|---|---|---|
| Endpoint | `invoice/InvoiceController.java` | `GET /api/v1/orders/{orderId}/invoice` → `produces = application/pdf` |
| Nghiệp vụ | `invoice/InvoiceService.java` | Gom dữ liệu + **scope quyền** (`requireScope`) |
| Render PDF | `invoice/InvoicePdfService.java` | Thymeleaf → `openhtmltopdf-pdfbox` 1.0.10 (đã khai báo ở `pom.xml`) |
| Template | `templates/invoice/order-invoice.html` | Tiếng Việt, có biến `paidAt` truyền riêng |
| Font | `resources/fonts/NotoSans-{Regular,Bold}.ttf` | ~630 KB mỗi file – có đủ |
| Tải về (JS) | `static/js/core/invoice.js` | `Invoice.download()` (blob + `<a download>`), `Invoice.open()` |
| Nút bấm | `js/admin/sales.js`, `js/manager/sales.js`, `js/member/history.js` | 3 trang đều gọi `Invoice.download(order)` |

Nguồn: commit `17e0688 "them in hoa don pdf va AI chatbot (chua xong)"`.

## 2. Kết quả đo thật (app tạm 8081)

```
GET /api/v1/orders/1/invoice  (Bearer admin)
→ HTTP 200 | Content-Type: application/pdf;charset=UTF-8 | 15.284 byte
→ Content-Disposition: attachment; filename="GYMFIT-ORD_SEED_000001.pdf"
   (RFC 2047: =?UTF-8?Q?GYMFIT-ORD=5FSEED=5F000001.pdf?=  +  filename*=UTF-8''…)
→ magic: %PDF-1.4     tail: %%EOF
```

Kết luận: file tải về là **PDF thật**, không phải HTML gắn đuôi `.pdf`.

## 3. Bộ test mới (13/13 PASS) – commit kèm

| File | Loại | Số test | Nội dung |
|---|---|---|---|
| `invoice/InvoicePdfServiceTest` | `@SpringBootTest` (renderer) | 2 | `render()` trả `%PDF-` + `%%EOF` + > 2.000 byte; trường hợp `providerReference/paidAt/branchPhone = null` vẫn render được |
| `invoice/InvoiceServiceTest` | Mockito thuần | 4 | `invoice_order_not_paid` (409), `invoice_out_of_scope` cho **hội viên** và cho **quản lý chi nhánh khác** (403), `order_not_found` (404) |
| `invoice/InvoiceEndpointTest` | MockMvc + JWT thật | 7 | admin/hội viên/quản lý tải được → `application/pdf` + `attachment`; **chưa đăng nhập bị chặn**; mã lỗi JSON đúng (`invoice_out_of_scope`, `order_not_found`); body bắt đầu bằng `%PDF-` |

Full suite sau khi thêm: **438/438 PASS** (`mvn test`, exit=0).

## 4. Ghi chú / điểm cần biết

- **I1 – scope quyền đã đúng** (viết sẵn): `ADMIN` xem mọi đơn; `BRANCH_MANAGER` **chỉ** chi nhánh của mình; `MEMBER` **chỉ** đơn của chính mình. Không có trường hợp "mọi người thấy mọi hóa đơn".
- **I2 – chỉ đơn `PAID` mới có hóa đơn**: đơn `PENDING_PAYMENT`/`CANCELLED` → 409 `invoice_order_not_paid`. Ngoài ra còn yêu cầu phải có bản ghi `payment.status = SUCCEEDED` (nếu mất → 404 `successful_payment_not_found`).
- **I3 – `Content-Type` đang là `application/pdf;charset=UTF-8`**: theo chuẩn PDF không cần `charset`. Không ảnh hưởng tải về (đã đo thực tế) và test dùng `contentTypeCompatibleWith` nên không vỡ. Để bỏ charset thì phải đổi cách set header của converter – **để nguyên, ghi nhận**.
- **I4 – `Invoice.open()` hiện không trang nào gọi** (chỉ `download` được dùng) – mã chết có chủ đích, giữ lại phòng khi cần nút "Xem trước".
- **I5 – không thêm dependency**: toàn bộ dùng `openhtmltopdf` + 2 font Noto Sans đã có sẵn trong repo.

## 5. Chưa làm / ngoài phạm vi

- Chưa tạo **nút "Xem trước"** (chỉ có "Tải về" như yêu cầu).
- Chưa có ảnh chụp màn hình nút Hóa đơn ở 3 trang (không có công cụ điều khiển trình duyệt trong phiên này) – thay bằng kiểm tĩnh: nút tồn tại, gắn `Invoice.download`, và E2E HTTP tải được file thật.
