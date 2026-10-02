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

### 1.8 Vấn đề bảo mật ngoài phạm vi chatbot (phát hiện khi scan, chưa sửa)

`SecurityConfig` đang `permitAll()` cho `/admin/**`, `/manager/**`, `/member/**`.
`UiController` không có `@PreAuthorize` → ai cũng tải được HTML dashboard (dữ liệu thì vẫn chặn ở API).
**Không thuộc danh sách file cho phép sửa trong plan → ghi nhận, không tự ý đổi.**
Nếu muốn vá: bỏ `/admin/**`,`/manager/**`,`/member/**` khỏi `permitAll` và thêm
`@PreAuthorize("hasRole('ADMIN')")` … trên `UiController`.