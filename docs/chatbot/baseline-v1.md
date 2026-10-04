# GYMFIT Chatbot — ĐƯỜNG CƠ SỞ V1 (V2-0)

> Chốt ngày **2026-10-04**, commit đầu nhánh công việc V2.
> Mục đích: mọi task V2-1…V2-16 phải **so sánh với file này** để biết có làm tốt hơn hay làm xấu đi.
> Lệnh tạo ra toàn bộ số liệu dưới đây nằm ở mục 6.

---

## 1. Quy mô & phiên bản

| Hạng mục | Giá trị |
|---|---|
| Số intent (mô hình) | **36** |
| Số feature (n-gram) | **10.258** |
| Mẫu train / val / test | **18.141 / 3.739 / 3.715** |
| Holdout viết tay (`chatbot/holdout.jsonl`) | **219 câu** (5–26 câu/intent) |
| File test chatbot | **16 file / 189 test** (`@Test` + `@ParameterizedTest`) |
| Full suite (`mvn test`) | **438/438 PASS** |
| Template câu trả lời (`templates.vi.json`) | 76 nhóm |
| Handler có mặt | 34 intent / 36 (log khởi động) |
| Ngưỡng | `threshold-accept = 0.70`, `threshold-clarify = 0.40` |
| PendingAction TTL | 5 phút |
| Rate limit | 20 lượt/phút/user (in-memory sliding window) |

---

## 2. Kết quả huấn luyện (chạy lại ngày 2026-10-04)

Siêu tham số lưới: **`lr0 = 0.1`, `l2 = 1e-6`, `epochs = 40`** — thời gian huấn luyện **33,7 giây**.

| Tập | Accuracy | Macro-F1 | Recall `OUT_OF_SCOPE` |
|---|---|---|---|
| **VAL** (sinh tự động) | 0,9719 | 0,9506 | 0,9593 |
| **TEST** (sinh tự động) | 0,9642 | 0,9487 | **0,7813** |
| **HOLDOUT** (viết tay — KPI thật) | **0,8813** | **0,8730** | 1,0000 |

→ Khe hở TEST/HOLDOUT ≈ **8 điểm** = bằng chứng dữ liệu sinh tự động "dễ" hơn câu thật (W3).

### 2.1 Intent yếu nhất

| Trên TEST (recall / F1) | Trên HOLDOUT (F1) |
|---|---|
| `REPORT_DASHBOARD` recall **0,50** → F1 0,640 | `FAQ_BUY_PLAN_HOWTO` **0,500** |
| `OUT_OF_SCOPE` F1 0,798 (P 0,815 / R 0,781) | `GREETING` **0,571** |
| `LIST_PLANS` F1 0,862 | `LIST_PLANS` 0,667 · `LIST_SERVICES` 0,667 |
| `FAQ_BUY_PLAN_HOWTO` F1 0,920 | `BRANCH_INFO` 0,714 · `BOOKINGS_TODAY` 0,727 |

### 2.2 Top cặp nhầm lẫn

| VAL | TEST | HOLDOUT |
|---|---|---|
| `LIST_PLANS → PLAN_DETAIL` 8 | `OUT_OF_SCOPE → FAQ_BUY_PLAN_HOWTO` 7 | `LIST_SERVICES → BRANCH_INFO` 2 |
| `FAQ_CHECKIN_HOWTO → FAQ_QR_HOWTO` 7 | `LIST_PLANS → PLAN_DETAIL` 7 | `FAQ_BUY_PLAN_HOWTO → OUT_OF_SCOPE` 2 |
| `OUT_OF_SCOPE → FAQ_BUY_PLAN_HOWTO` 4 | `PLAN_RECOMMEND → LIST_SERVICES` 5 | `GREETING → LIST_PLANS/OUT_OF_SCOPE/AUDIT_RECENT` 1 mỗi loại |
| `CONFIRM_YES → THANKS` 4 | `FAQ_CANCEL_POLICY → BOOKING_CANCEL` 4 | `MY_BOOKINGS → BOOKINGS_TODAY` 1 |

### 2.3 Đặc điểm dữ liệu train (đo trên `grammar.json` sinh ra)

| Đặc điểm | Hiện tại | Mục tiêu V2 (mục 6.1 plan) |
|---|---|---|
| Độ dài câu | TB **8,7 từ**, p90 = 12, max 17 | ≥ 15% câu ≥ 15 từ |
| Từ vựng | **1.404** từ | ≥ 4.000 |
| Câu ghép / có "?" / tiếng Anh | 5% / 4% / 2,5% | ≥ 10% / ≥ 25% / ≥ 6% |
| "Chào + yêu cầu" trong train | **3 câu** | ≥ 600 câu |
| `OUT_OF_SCOPE` | 718 câu (4%), 518 câu liên quan gym | ≥ 3.000 (6–8%), 6 loại con |

---

## 3. Độ trễ (p95) — 200 câu thật

| Chỉ số | Giá trị |
|---|---|
| Số câu | **200** (40 câu × 5 vòng, có dấu, 40 intent-đại-diện) |
| p50 | **40,1 ms** |
| **p95** | **110,6 ms** |
| mean / min / max | 50,5 / 19,0 / 268,2 ms |
| Điều kiện đo | HTTP thật `POST /api/v1/chat`, token `member1`, app cổng 8081, **bao gồm** ghi `chat_session` + `chat_message` vào SQL Server |
| Cổng V2 (mục 7.2 plan) | p95 **< 300 ms, không tính DB** → số đo trên đã **bao gồm DB** nên còn dư địa |

Cách đo: bật `--gymfit.chatbot.rate-limit-per-minute=1000000` lúc khởi động app (chỉ để đo, **không** sửa `application.yml`),
warmup 1 câu rồi bấm giờ từng request bằng `Stopwatch`. Script: `v2_baseline_p95.ps1` (Temp/opencode).

> ⚠️ Lưu ý kỹ thuật: `Invoke-RestMethod` của PowerShell 5.1 phải ghi
> `-ContentType "application/json; charset=utf-8"` thì câu tiếng Việt mới gửi đúng UTF-8;
> thiếu `charset` ⇒ server trả **500** `Invalid UTF-8 middle byte`.

---

## 4. Ba khoảng trống được xác nhận bằng code (đầu việc V2-0 yêu cầu ghi nhận)

| # | Ghi nhận | Bằng chứng |
|---|---|---|
| 1 | **`chat/admin` rỗng** → vòng "gán nhãn → huấn luyện lại" chưa khép kín | `src/main/java/com/gymfit/chat/admin/` **không có file nào**; không có `ChatbotAdminController`; không có trang `/admin/chatbot` |
| 2 | **Chưa có bộ kịch bản hội thoại** | `src/test/resources/scenarios/` **không tồn tại**; không có `ScenarioRunnerTest`/`ScenarioFixtures` |
| 3 | **Tiền tố mã đơn = `ORD_`** (đóng mục 1.2 "verify tại T9" trong NOTES) | `OrderService` dòng 120 và `MemberPurchaseService` dòng 102: `CodeGenerator.generate("ORD")`. Regex entity `\b(BOOK\|ORD\|…\|PAY)_[0-9A-F]{16}\b` đã bao trúng. Kèm tiền tố khác: `BOOK_`, `PAY_`, `MOMO_` |

### 4.1 Thêm phát hiện từ lúc đo p95

- **`sessionId` không phải UUID hợp lệ → server tạo session MỚI mỗi lượt.**
  `ChatSessionService.loadOrCreate` chỉ tái sử dụng khi `findByIdAndUserId(sessionId, userId)` khớp,
  ngược lại `UUID.randomUUID()` (dòng 105). Đo 200 câu ⇒ **201 session** mới được tạo.
  ⇒ Frontend **bắt buộc** lấy `sessionId` từ `ChatResponse` (đã có từ T10) và gửi lại ở lượt sau;
  nếu `chat.js` tự sinh chuỗi tùy ý thì mỗi lượt là phiên mới → **mất toàn bộ slot/xác nhận**.
- Gửi `sessionId` của **user khác** → lặng lẽ tạo session mới, không lộ (đã kiểm tra: `findByIdAndUserId` kèm `userId`).

---

## 5. Chưa có ở V1 (để V2 lấp)

- Không có `ChatbotAdminController`, chưa export được JSONL từ `chat_training_candidate`.
- Không có `holdout_v2.jsonl` (schema `tier/tags/role`), không có `safety.json`.
- Không có `ScenarioRunnerTest`, không có fixture nào.
- Không có `FaqRetriever`/`faq_kb.json` (FAQ = 6 intent rời, `faq.json` chỉ dùng cho câu trả lời).
- Không có `SocialWrapperStripper`, `Segmenter`, `AspectExtractor`, `OodGuard`, `FlowEngine`.
- Chưa đo: recall@1 FAQ, entity/slot F1, tỷ lệ fallback trên câu NATURAL, độ trễ p95 **không tính DB**.

---

## 6. Lệnh đã chạy (tái lập baseline)

```powershell
.\mvnw.cmd -q test                 # → 438/438 PASS
.\mvnw.cmd -q exec:java@train      # → 33,7 s; ghi docs/chatbot/training-report.md
                                   #   + src/main/resources/chatbot/model/intent-model.bin
powershell -File v2_baseline_p95.ps1   # → p95 = 110,6 ms / 200 câu
```

Số liệu test chatbot: đếm `@Test|@ParameterizedTest` trong `src/test/java/com/gymfit/chat/**` → **189**.
Dữ liệu benchmark đã dọn: `chat_session`/`chat_message` của lượt đo (còn 10 session / 116 message là của các lần E2E trước).
