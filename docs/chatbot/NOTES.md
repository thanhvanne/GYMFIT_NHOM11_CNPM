# NOTES — GYMFIT Chatbot

Ghi lại các điểm **đã đối chiếu với code thật** (ngày 2026-10-03, commit `17e0688`, branch `chatbot`).
Khi plan và code thật mâu thuẫn → **tin code thật** và ghi vào đây.

---

## 1. Xác nhận chữ ký service (đã đọc code, dùng cho T9/T10)

| Service | Chữ ký thật |
|---|---|
| `BranchService` | `list(BranchStatus)`, `get(Long)`, `getServices(Long branchId)`, `getOperatingHours(Long branchId)` |
| `FacilityService` | `list(AppPrincipal, Long branchId, ServiceCode, FacilityStatus)`, `get(AppPrincipal, Long)` |
| `PlanManagementService` | `list(AppPrincipal, Long branchId, PlanStatus)`, `get(AppPrincipal, Long)` |
| `ProductService` | `list(ProductStatus)`, `get(Long)` |
| `MembershipService` | `current(AppPrincipal, Long memberId)`, `history(AppPrincipal, Long memberId)` |
| `BookingService` | `create(AppPrincipal, BookingCreateRequest)`, `list(AppPrincipal)`, `get(AppPrincipal, Long)`, `cancel(AppPrincipal, Long, BookingCancelRequest)`, `availability(AppPrincipal, Long facilityId, LocalDate date)` |
| `CheckInService` | `list(AppPrincipal)`, `manual(...)`, `qr(...)` |
| `OrderService` | `list(AppPrincipal)`, `get(AppPrincipal, Long)`, `create(...)` |
| `ReportService` | `dashboard(AppPrincipal)`, `revenue(AppPrincipal, LocalDate from, LocalDate to, Long requestedBranchId)`, `services(AppPrincipal, LocalDate, LocalDate, Long)` |
| `InventoryService` | `list(AppPrincipal, Long branchId)`, `movements(AppPrincipal, Long branchId)`, `adjust(...)` |
| `AuditService` | `list(Long branchId)` — **không kiểm tra role**, handler phải tự chặn ADMIN |

### 1.1 Scope đã được service tự ép (không cần bot tự lọc)

- `BookingService.list` → ADMIN: toàn hệ thống · BRANCH_MANAGER: theo `principal.branchId` · MEMBER: theo `principal.memberId`.
- `CheckInService.list` → scope y hệt `BookingService.list`. ✅
- `OrderService.list` → cần verify đã scope (T9 sẽ đọc kỹ thêm).
- `BookingService.availability` → MEMBER **bắt buộc** có membership hợp lệ (`membershipService.requireEligible`), ném `ConflictException`. Ngoài ra trả `List.of()` (không ném) nếu facility/branch không ACTIVE, `bookingEnabled=false`, hoặc chi nhánh **không mở cửa** vào `dayOfWeek` đó → **coi là "không có slot"**, không phải lỗi.
- `ReportService.*` → MANAGER bị ép theo chi nhánh (đã xác nhận qua `BranchScopeGuard`).

### 1.2 Mã sinh tự động — `CodeGenerator`

```java
CodeGenerator.generate(prefix) -> prefix + "_" + 16 hex upper
```
- `bookingCode`: `BOOK_<16HEX>` (xem `BookingService.create`)
- `orderCode`: kiểm tra tại `OrderService.create` — **verify tại T9**
- Regex entity extractor (T2): `\b(BOOK|ORD|PLAN|MEM|PROD|BILL|PAY)_[0-9A-F]{16}\b`

### 1.3 `GlobalExceptionHandler` — **KHÔNG hỗ trợ HTTP 429**

Các handler có: `ApiException`, `MethodArgumentNotValidException` (422), `BadCredentials` (401),
`Locked`/`Disabled`/`AccessDenied` (403), `DataIntegrityViolation` (409), `Exception` (500).

→ Theo T7.4: rate-limit **không** ném 429. Trả `ChatResponse` 200 kèm câu `error.rate`.
(Thêm `TooManyRequestsException` sau nếu cần; hiện chưa thêm để giữ diff nhỏ.)

### 1.4 `AppPrincipal` — không có setter

`@Getter` + final fields, khởi tạo từ `AppUser(user)`. Getter: `getUserId()`, `getBranchId()`,
`getMemberId()`, `getFullName()`, `getEmail()`, `getPasswordHash()`, `getRole()`, `getStatus()`.
→ Test phải dựng `AppUser` thật rồi `new AppPrincipal(user)`, không mock trực tiếp được.

### 1.5 `ApiException`

`ApiException(HttpStatus status, String code, String message)`, có `getStatus()`, `getCode()`.
Subclass: `BadRequestException`, `ConflictException`, `ForbiddenException`, `NotFoundException`, `UnauthorizedException`.

### 1.6 `AuditEvent` — style entity chuẩn của dự án

`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="audit_event")`,
`@Id @GeneratedValue(IDENTITY)`, cột `@Column(name="created_at_utc", nullable=false) private Instant`,
`@Column(columnDefinition = "NVARCHAR(MAX)")` cho JSON. → `ChatSession`/`ChatMessage`/`ChatTrainingCandidate` viết y hệt.

### 1.7 ⚠ MÂU THUẪN PLAN vs CODE

1. **Reason check-in bị từ chối: có 5, không phải 4.**
   Plan mục 1.2 liệt kê 4 (`NO_ACTIVE_MEMBERSHIP`, `SERVICE_NOT_INCLUDED`, `MEMBERSHIP_BRANCH_MISMATCH`, `MEMBERSHIP_INVALID`).
   Code thật (`CheckInService` ~dòng 294-345) có thêm **`DUPLICATE_CHECKIN`**
   (đã check-in ACCEPTED cùng dịch vụ trong `DUPLICATE_WINDOW`).
   → `Fmt.rejectReason()` + `faq.json` phải có **5** reason. Bot báo đủ 5.

2. **Rule hủy lịch 2 giờ chỉ áp cho MEMBER.**
   `BookingService.cancel` chỉ kiểm `startsAt - 2h` khi `role == MEMBER`.
   → `cancel.too_late` chỉ áp cho MEMBER (đúng như plan T10.5 vì cancel là intent MEMBER).

3. **`BookingService.availability` trả slot theo `bookingDurationMinutes` của config chi nhánh** (mặc định seed 60), không cố định 60 như plan giả định. → Bot phải đọc `BranchServiceConfigResponse.bookingDurationMinutes`, không hardcode.

4. **`ChatResponse` hiện chỉ có `(String message, Instant createdAtUtc)`** — sẽ đổi ở T10 (thêm `sessionId`, `intent`, `confidence`, `suggestions`, `card`, `messageId`).

5. **`application.yml` không có `gymfit.chatbot`** → thêm ở T0.

6. **`application.yml` hiện có `gymfit.ai.*` với `model: gemini-3.8-flash`** → xoá ở T0 (tên model không tồn tại, nhưng cũng không dùng nữa).

### 1.8 ⚠ Bug đã tìm ra khi làm T1–T2 (đã sửa, ghi lại để không tái phát)

1. **`_` bị normalizer xoá** → mã `BOOK_0123…` thành `book 0123…`, hỏng entity `BOOKING_CODE`.
   Đã thêm `_` vào bảng ký tự giữ lại.
2. **Rút gọn ký tự lặp phá nát số tiền/ngày**: `600000` → `600`, `500.000` → `500.00`.
   Đã giới hạn rút gọn **chỉ với chữ** (`([a-z])\1{2,}`).
3. **"thứ 2" ≠ ISO 2.** `thứ 2` là Thứ Hai = `DayOfWeek.MONDAY` (ISO 1). Map sai làm
   "thứ 2" ra thứ 3. Đã sửa `DayOfWeek.of(vietnamese - 1)`; `chủ nhật` → `SUNDAY` (7).
4. **Điều kiện "buổi" bị đảo** trong `sessionAfter()`: `between.isEmpty()` lại trả `NONE`,
   nên "7h tối" ra 07:00. Đã sửa (khoảng trắng rỗng = buổi nằm ngay sau giờ).
5. **`toDate()` trả null khi thiếu tháng** → "ngày 5" không ra ngày. Đã tách nhánh
   "chỉ có ngày" (dùng tháng hiện tại, đã qua thì lùi 1 tháng).
6. **Lùi tháng/ngày áp nhầm lên khoảng báo cáo** → "từ 1/10 đến hôm nay" ra 11/01.
   Đã thêm tham số `rollDayForward`/`rollToNextYear`: `dd/mm` tường minh thì giữ nguyên năm/tháng.
7. `matcher.end()` gọi cả khi `find()` trả `false` → `IllegalStateException: No match available`.
8. `BigDecimal` từ `1.5 * 1e6` ra scale 1 (`1500000.0`) → so sánh bằng `equals()` luôn fail.
   Đã chuẩn hoá về scale 0 trong `Collector.money()`.
9. `DayOfWeek.with(MONDAY)` lùi về đầu tuần ISO **cùng tuần** (thứ 7 → thứ 2 = 28/09),
   nên "tuần này" luôn có range hợp lệ. Dùng `minusWeeks(1)` sẽ sai khi hôm nay là T7/CN.

### 1.9 Quy ước đã chốt cho phần còn lại

- **"1 triệu 5" = 1.500.000** (rút gọn "triệu rưỡi"), `"1.5 triệu"` cũng vậy.
- **`"mot"` chỉ nhận khi có tiền tố "ngày"** ("ngày một" = ngày kia). `"mot"` trần bị bỏ qua
  để tránh nhầm với số lượng.
- Giờ ≤ 5 **không kèm buổi** → +12; giờ 6–11 không buổi → giữ nguyên (7h → 07:00).
- `CN` trần chỉ hiểu là Chủ nhật khi có "thứ/vao/ngày" phía trước.
- `DbGazetteerProvider` cần scheduler → thêm `nlu/ChatbotSchedulingConfig` với
  `@EnableScheduling` (không sửa `GymFitApplication`). Khoá scheduler: `gymfit.chatbot.gazetteer-refresh-ms`.

### 1.10 Thông tin môi trường (không hardcode vào code)

SQL Server local: user `sa`. `application.yml` đang để mặc định `DB_PASSWORD:123456` —
**không khớp** với instance thật. Khi chạy/test có DB phải truyền biến môi trường
`DB_PASSWORD`, **không** sửa/hardcode mật khẩu vào repo.

### 1.11 Vấn đề bảo mật ngoài phạm vi chatbot (phát hiện khi scan, chưa sửa)

`SecurityConfig` đang `permitAll()` cho `/admin/**`, `/manager/**`, `/member/**`.
`UiController` không có `@PreAuthorize` → ai cũng tải được HTML dashboard (dữ liệu thì vẫn chặn ở API).
**Không thuộc danh sách file cho phép sửa trong plan → ghi nhận, không tự ý đổi.**
Nếu muốn vá: bỏ `/admin/**`,`/manager/**`,`/member/**` khỏi `permitAll` và thêm
`@PreAuthorize("hasRole('ADMIN')")` … trên `UiController`.

---

## 2. Ghi nhận khi làm T9–T10 (đối chiếu với code thật)

1. **DTO trả lời của service nằm ở package `*.dto`**: `com.gymfit.booking.dto.BookingResponse`,
   `com.gymfit.checkin.dto.CheckInResponse`, `com.gymfit.order.dto.OrderResponse` — không phải
   package mẹ. Import sai là lỗi compile hay gặp nhất khi viết handler.
2. **`BranchResponse` là record** → dùng `name()`, `phone()`, `id()` (không có `getName()`).
3. **`ConversationState` phải truyền vào handler** qua `HandlerContext` (component thứ 8,
   constructor 7 tham số giữ nguyên cho test): handler tự đặt `awaiting`/`pending`/`slots`,
   `DialogueManager` không biết chi tiết từng luồng.
4. **Thao tác ghi dữ liệu chỉ đi qua `BookingActionExecutor`** — `BookingFlowHandler` *lập kế
   hoạch* và trả thẻ `CONFIRM`; `DialogueManager` mới gọi executor khi nhận `CONFIRM:<uuid>`
   (hoặc câu "đúng rồi"/"ok"). Nhầm intent ⇒ không thể nào ghi được dữ liệu.
5. **`PendingAction` được dọn TRƯỚC khi thực thi** → bấm Xác nhận 2 lần không tạo 2 lịch.
6. **Mã lịch chỉ nhận format `BOOK_<16HEX>`** (regex ở mục 1.2). Test dùng
   `BOOK_1234567890ABCDEF`; `BK001` KHÔNG được trích ra → luồng hủy không chọn được lịch.
7. **`app_user` có `CHECK CK_app_user_scope`**: `MEMBER` bắt buộc `branch_id` và `member_id`
   khác NULL (FK `member`). Test tích hợp muốn tạo user nhanh → dùng `ADMIN`
   (cả hai đều NULL), không phải `MEMBER`.
8. **`application.yml`**: `threshold-accept: 0.70`, `threshold-clarify: 0.40`,
   `rate-limit-per-minute: 20`, `pending-action-ttl-minutes: 5` — `DialogueManager` đọc qua
   `@Value` rồi đẩy vào `RateLimiter.setLimit()` lúc `@PostConstruct`.
9. **`ChatRequest.payload`** nhận `TEXT:<s>` | `CONFIRM:<uuid>` | `CANCEL:<uuid>`;
   `FeedbackRequest` có thêm `sessionId` để không cho đánh giá tin nhắn của người khác.
10. **Template mới thêm ở T10** (đã có test `requiredKeysExist` bỏ qua, chỉ kiểm `> 40` khoá):
    `pending.cancelled`, `clarify.no`, `error.empty`, `booking.no_member`.
    Sửa luôn chuỗi mojibake cũ của `cancel.ask_pick`.
11. **Frontend `chat.js` giữ `sessionId` trong `sessionStorage`** — nếu không, mỗi lượt là một
    phiên mới và toàn bộ slot/xác nhận mất tác dụng.
12. **⚠ BUG thật tìm ra lúc demo (đã sửa): `BookingService` đọc giờ hoạt động qua JPA** →
    Hibernate đọc cột `time` của SQL Server **lệch +8 giờ** (`06:00` trong DB ra `14:00`,
    `22:00` ra `06:00`) → `open > close` → vòng lặp sinh slot chạy 0 lần → **`availability`
    trả rỗng cho mọi facility/ngày**, và `create()` chê mọi lịch là
    `booking_outside_operating_hours`. Trong khi đó `BranchService.getOperatingHours`
    **đã** dùng `BranchOperatingHourJdbcRepository` (JDBC thuần) nên trang admin vẫn hiện đúng
    06:00–22:00 → hai đường đọc lệch nhau, khó phát hiện.
    **Cách sửa:** `BookingService` chuyển sang `BranchOperatingHourJdbcRepository`, xoá luôn
    `BranchOperatingHourRepository` (JPA) để không ai dùng lại.
    Test chống tái phát: `BookingAvailabilityTest` (giờ đọc được phải khớp DB + đặt lịch trong
    giờ phải thành công). Chưa xác định được cơ chế chính xác (+8 giờ trong khi JVM là +7 và
    `hibernate.jdbc.time_zone: UTC`) — **không** đụng `jdbc.time_zone` vì mọi cột `Instant`
    (`created_at_utc`…) đang phụ thuộc nó.
13. **Server đang chạy không tự nạp code mới** — `mvnw spring-boot:run`/IntelliJ nạp class lúc
    khởi động; code compile sau đó không áp dụng. Dấu hiệu: chat trả đúng câu placeholder T0
    *"Trợ lý GYMFIT đang được nâng cấp..."* dù code đã sửa → phải **restart** server.

---

## 3. Ghi nhận V2-0 — Chốt đường cơ sở (2026-10-04)

Số liệu đầy đủ: **`docs/chatbot/baseline-v1.md`** (train/val/test/holdout, p95, khoảng trống).

1. **`chat/admin` rỗng** — `src/main/java/com/gymfit/chat/admin/` không có file nào,
   không có `ChatbotAdminController`, không có trang `/admin/chatbot`
   → vòng **gán nhãn → export → huấn luyện lại** chưa khép kín (V2-1).
2. **Chưa có bộ kịch bản hội thoại** — `src/test/resources/scenarios/` không tồn tại,
   không có `ScenarioRunnerTest`/`ScenarioFixtures` (V2-2).
3. **Tiền tố mã đơn = `ORD_`** → *đóng mục 1.2 "verify tại T9"*:
   `OrderService` dòng 120 và `MemberPurchaseService` dòng 102 đều gọi
   `CodeGenerator.generate("ORD")`. Regex entity `\b(BOOK|ORD|PLAN|MEM|PROD|BILL|PAY)_[0-9A-F]{16}\b`
   đã bao trúng. Các tiền tố khác cũng xác nhận: `BOOK_`, `PAY_`, `MOMO_`.
4. **`sessionId` không phải UUID của chính user ⇒ server tạo session MỚI** (mục 4.1 baseline):
   `ChatSessionService.loadOrCreate` dòng 68–95 chỉ tái sử dụng khi
   `findByIdAndUserId(sessionId, userId)` khớp, nếu không thì `create()` với `UUID.randomUUID()`.
   Đo 200 câu ⇒ 201 session mới. Frontend **bắt buộc** nhận `sessionId` từ `ChatResponse`
   rồi gửi lại ở lượt sau, nếu tự sinh chuỗi tùy ý thì mỗi lượt là phiên mới → mất slot/xác nhận.
5. **Không được sửa `application.yml` khi đo**: rate-limit 20/phút sẽ cắt sau 20 câu.
   Khi đo p95 chạy app với `--gymfit.chatbot.rate-limit-per-minute=1000000` (chỉ lúc đo).
6. **Ghi chú script `.ps1` tiếng Việt**: PowerShell 5.1 gửi body theo ANSI ⇒ server trả
   **500** `Invalid UTF-8 middle byte`. Phải ghi
   `-ContentType "application/json; charset=utf-8"` **và** lưu file `.ps1` có **BOM**.

---

## 4. Ghi nhận V2-1 — Khép vòng gán nhãn (2026-10-04)

### 4.1 Đã làm

| Thành phần | File |
|---|---|
| API | `chat/admin/ChatbotAdminController.java` (5 endpoint, `@PreAuthorize("hasRole('ADMIN')")` **ở cấp class**) |
| Nghiệp vụ | `chat/admin/ChatbotAdminService.java` |
| DTO | `chat/admin/dto/{CandidateResponse,CandidatePageResponse,CandidateLabelRequest,IntentOptionResponse,FeedbackCountResponse,ChatbotStatsResponse}.java` |
| Trang | `templates/admin/chatbot.html` + `static/js/admin/chatbot.js` |
| Route | `UiController` → `GET /admin/chatbot` |
| Menu | thêm mục **Chatbot** vào **12 file** `templates/admin/*.html` |
| Repo | `ChatTrainingCandidateRepository.findTop20ByStatus…`, `ChatMessageRepository.{findByRoleAndCreatedAtUtcAfter,findTop500ByFeedbackNotNullOrderByIdDesc}` |
| Test | `chat/admin/ChatbotAdminEndpointTest` — **14 test** |

API: `GET /candidates?status&page&size` · `PUT /candidates/{id}` `{label,status}` ·
`GET /intents` · `GET /export` (JSONL, `application/jsonl`, `attachment; filename="seed_from_logs.jsonl"`) ·
`GET /stats`.

Mã lỗi: `page_invalid` / `size_invalid` (giới hạn 1–100) / `candidate_status_invalid` /
`candidate_label_required` / `candidate_label_unknown` / `candidate_not_found`.

### 4.2 E2E HTTP thật (app 8081) — 9/9 PASS

```
1. stats 200      tin24h=43  fallback=0.186  cho=21  daGan=0
2. intents 200    36 nhãn (GREETING / SMALLTALK)
3. candidates 200 total=21 page=0 items=5
4. PUT gắn nhãn   200 id=62 status=LABELED label=LIST_PLANS labeledAtUtc=…Z
5. export 200     attachment; filename="seed_from_logs.jsonl"
                  {"text":"E2E V2-1 candidate","intent":"LIST_PLANS","group":"CAND#62"}
6. quyền          manager=403  member=403  chưa đăng nhập=403
7. nghiệp vụ      nhãn không tồn tại → 400
8. GET /admin/chatbot 200 (5.686 ký tự), đủ JS + nội dung
9. menu Chatbot có ở /admin/users
```

Full suite sau V2-1: **452/452 PASS** (`mvn test`, exit=0).

### 4.3 Lỗi đã gặp + phát hiện

- **N10 `created_at_utc` không có `@PrePersist`** – bảng có
  `DEFAULT SYSUTCDATETIME()` nhưng Hibernate vẫn **INSERT NULL** khi entity để trống
  (mapper sinh cột đầy đủ) ⇒ `INSERT fails`. Mọi chỗ tự `builder()` phải set
  `.createdAtUtc(TimeUtil.now())` như `ChatSessionService.createCandidate` đang làm.
- **N11 Định nghĩa "% fallback"** = câu trả lời bot có `intent = OUT_OF_SCOPE`
  **hoặc** `confidence < threshold-clarify (0,40)` — đúng nhánh `DialogueManager.gate`.
  Lưu ý: câu OOS **có độ tin cậy cao** vẫn là fallback nhưng **không** tạo candidate
  (`recordCandidate` chỉ ghi khi `confidence < clarify`) → `candidates` ≠ tổng số fallback.
- **N12 `seed_from_logs.jsonl` chưa tồn tại** trong `src/main/resources/chatbot/`
  (`DatasetGenerator.loadSeedFromLogs` bỏ qua nếu thiếu file) ⇒ export là bước đầu tiên
  của vòng huấn luyện, chưa có mẫu nào để so.
- **N13 Menu admin không có fragment** – sidebar chép tay trong **12** template;
  thêm 1 trang admin là phải sửa cả 12 file (đã làm, neo vào `</nav>`).
- **N14 Còn 20 ứng viên `PENDING`** (id 13–32) tạo ra từ các lượt chat test/benchmark:
  "tôi muốn gặp nhân viên", "mã hội viên của tôi là gì", "có sân pickleball không"…
  Đây là câu **thật** bot trả lời chưa chắc nên **giữ lại làm dữ liệu demo**;
  muốn xóa:
  `DELETE FROM chat_training_candidate WHERE status='PENDING';`

### 4.4 Chưa làm / ngoài phạm vi V2-1

- Chưa có nút "huấn luyện lại" từ trang admin (phải chạy tay `mvn exec:java@train`) — theo plan,
  vòng huấn luyện nằm ở V2-14.
- Chưa có `GET /api/v1/chatbot/kb/reload` (P2, mục 5.3 plan).
- Chưa đếm được **tỷ lệ 👎 theo % trên tổng số câu trả lời** (chỉ có % 👎 trên các câu
  **được đánh giá**) — thiếu cột tổng đánh giá theo intent.