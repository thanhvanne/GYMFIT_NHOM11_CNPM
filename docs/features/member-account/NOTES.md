# NOTES – Tự động cấp tài khoản + mật khẩu khi thêm hội viên

> Ghi chú đối chiếu **code thật** với `PLAN.md`. Mâu thuẫn → **tin code** (quy tắc mục 0.6).
> Ngày xác minh: 2026-10-04. Nhánh `main` @ `6a3037a`.

---

## F0 – Kết quả xác minh 7 mục (chưa sửa code)

### F0.1 – Nơi dùng `POST /api/v1/members` và `MemberResponse`

**Lệnh:**

```powershell
grep -rn "/api/v1/members" src/main/resources/static/js
grep -rn "new MemberResponse" src
```

**Kết quả gọi `POST /api/v1/members` (chỉ 2 chỗ – đây là nơi đổi response ở F3/F6):**

| File | Dòng | Ghi chú |
|---|---|---|
| `static/js/admin/members.js` | 394 | `await Api.post("/api/v1/members", data)` – **bỏ qua body trả về**, chỉ `showSuccess` + `loadMembers()` |
| `static/js/manager/members.js` | 338 | tương tự, `Api.post("/api/v1/members", common)` |

→ **Hệ quả:** đổi kiểu trả về `MemberResponse` → `MemberCreateResponse` **không phá** 2 chỗ này
(chúng không đọc response). F6 mới phải đọc `data.member` / `data.account`.

**Chỉ đọc GET (không bị ảnh hưởng – thêm field là backward compatible):**
`admin/dashboard.js:56`, `admin/bookings.js:179`, `admin/checkins.js:115`, `admin/users.js:128`,
`admin/sales.js:99`, `manager/dashboard.js:45`, `manager/bookings.js:33`, `manager/sales.js:31`,
`manager/checkins.js:31`, `member/home.js:29`, `member/plans.js:26`, `member/qr.js:18`,
`member/profile.js:21`, `member/booking.js:25`.

**`new MemberResponse(...)` trong Java: chỉ 1 chỗ** → `member/MemberService.java:332` (`toResponse`).
(F5 thêm 2 field chỉ cần sửa đúng chỗ này + mọi lời gọi `toResponse`.)

### F0.2 – `MemberController`: quyền và mã HTTP

```java
@GetMapping            @PreAuthorize("isAuthenticated()")                      // list
@GetMapping("/{id}")   @PreAuthorize("isAuthenticated()")                      // get
@PostMapping           @ResponseStatus(HttpStatus.CREATED)                     // 201
                       @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')") // create
@PutMapping("/{id}")   @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')") // update
```

→ F3 giữ nguyên quyền + mã **201**, chỉ đổi kiểu trả về thành `MemberCreateResponse`.
Endpoint mới `POST /{id}/account` và `/{id}/account/reset-password` sẽ dùng đúng
`@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")` + kiểm chi nhánh ở service (D4).

### F0.3 – `audit_event` có CHECK giới hạn `action` không?

**Lệnh:** đọc `database/gymfit.sql:892-930` (`CREATE TABLE audit_event`).

```sql
action       VARCHAR(60)  NOT NULL,          -- KHÔNG có CHECK danh sách action
entity_type  VARCHAR(60)  NOT NULL,
CONSTRAINT CK_audit_event_json
    CHECK (details_json IS NULL OR ISJSON(details_json) = 1)
```

→ **Kết luận: KHÔNG cần sửa DB** cho `USER_CREATED` / `PASSWORD_RESET` / `PASSWORD_CHANGED`.
Mục 5 của F1 (nếu F0 thấy có CHECK) = **bỏ qua**, ghi lại ở đây để khỏi sửa nhầm.
Chỉ cần đảm bảo `details_json` là JSON hợp lệ (**không chứa mật khẩu** – đã là yêu cầu mục 0.2).

### F0.4 – `JwtAuthenticationFilter` xử lý token sai / user bị khóa & định dạng lỗi

**Code:** `common/security/JwtAuthenticationFilter.java` (73 dòng, đọc ở F0):

- Thiếu header `Bearer` → `filterChain.doFilter` ngay (không set authentication).
- `token` hỏng / user không tồn tại / `status != ACTIVE` → nhánh `catch (Exception ignored) {}`
  → **im lặng, không log, không set authentication** → request đi tiếp và bị
  `anyRequest().authenticated()` chặn.

**Định dạng lỗi thực tế đo được (server đang chạy, `GET /api/v1/members`):**

```json
// không có token  → HTTP 403
{"timestamp":"2026-10-04T03:26:40.884+00:00","status":403,"error":"Forbidden","path":"/api/v1/members"}
// token sai       → HTTP 403
{"timestamp":"...","status":403,"error":"Forbidden","path":"/api/v1/members"}
```

→ đây là **default error của Spring**, **KHÔNG** phải `ApiError` của app.

**Định dạng `ApiError` chuẩn (phải copy y như vậy cho `password_change_required` ở F4):**

```java
// common/error/ApiError.java
public record ApiError(Instant timestamp, int status, String code,
                       String message, String path, Map<String,String> errors) {}

// common/error/GlobalExceptionHandler.java:29
new ApiError(Instant.now(), status.value(), exception.getCode(),
             exception.getMessage(), request.getRequestURI(), Map.of())
```

Ví dụ JSON cần trả ở F4:
```json
{"timestamp":"<Instant>","status":403,"code":"password_change_required",
 "message":"Bạn cần đổi mật khẩu trước khi sử dụng hệ thống",
 "path":"/api/v1/bookings","errors":{}}
```
**Lưu ý:** filter phải tự `response.setStatus(403)`, `Content-Type: application/json`,
`response.getWriter().write(...)` rồi **`return`** (không `doFilter` tiếp).

### F0.5 – `auth.js` và `UiController`

**`static/js/core/auth.js` (81 dòng):**

| Hàm | Hành vi |
|---|---|
| `Auth.save(loginResponse)` | ghi `localStorage`: `gymfit_access_token` = token, `gymfit_current_user` = `JSON.stringify(user)` → **`mustChangePassword` nếu thêm vào `user` sẽ tự nằm đây** |
| `Auth.user()` | đọc + `JSON.parse` từ localStorage |
| `Auth.redirectByRole(user)` | `switch (user.role)`: ADMIN→`/admin/dashboard`, BRANCH_MANAGER→`/manager/dashboard`, MEMBER→`/member/home`, khác→`logout()` |
| `Auth.requireRole(role)` | không token/user → `/login`; sai vai trò → `redirectByRole`; trả `user` |

→ **Chưa có** kiểm `mustChangePassword` ở bất kỳ đâu ⇒ F7 phải chèn vào `redirectByRole`
hoặc đầu `requireRole` (đề xuất: một hàm chung `guard(user)` để mọi trang gọi).

**`ui/UiController.java` – pattern khai báo route trang:**

```java
@GetMapping("/login")
public String login() { return "login"; }   // trả tên view Thymeleaf: templates/login.html

@GetMapping("/")       { return "redirect:/login"; }
@GetMapping("/member/home") { return "member/home"; }
```

→ F7 thêm `@GetMapping("/change-password") → "change-password"` theo đúng mẫu này.

**`common/security/SecurityConfig.java:35-48` – danh sách `permitAll`:**
`"/"`, `"/login"`, `"/admin/**"`, `"/manager/**"`, `"/member/**"`, `"/assets/**"`, `"/css/**"`,
`"/js/**"`, `"/img/**"`, `"/api/v1/auth/login"`, `"/error"` → **phải thêm `"/change-password"`**
(nếu thiếu, trang trả 403 – danh sách lỗi thường gặp ở mục 8).

### F0.6 – Số hội viên chưa có tài khoản

**Lệnh seed (đếm trên file `database/gymfit.sql`):**

```powershell
# 10 địa chỉ email trong INSERT INTO member, 8 trong INSERT INTO app_user
# (1 ADMIN + 4 BRANCH_MANAGER + 3 MEMBER: member1–3@gymfit.local)
```

**Lệnh SQL chạy thật trên DB (đã chạy, kết quả bên dưới):**

```sql
SELECT COUNT(*) AS tong_hoi_vien FROM member;

SELECT COUNT(*) AS hoi_vien_chua_co_tai_khoan
FROM member m
WHERE NOT EXISTS (SELECT 1 FROM app_user u WHERE u.member_id = m.id);

SELECT m.member_code, m.email,
       CASE WHEN u.id IS NULL THEN 'CHUA CO' ELSE 'CO' END AS tai_khoan
FROM member m LEFT JOIN app_user u ON u.member_id = m.id
ORDER BY m.member_code;
```

**Kết quả trên DB thực tế (2026-10-04): 12 hội viên, 9 chưa có tài khoản.**

| member_code | email | tài khoản |
|---|---|---|
| GF000001–GF000003 | member1–3@gymfit.local | **CÓ** |
| GF000004–GF000010 | member4–10@gymfit.local | CHƯA CÓ (7) |
| GF35918869 | `m@tkhau2006` | CHƯA CÓ (dữ liệu tạo tay) |
| GF85902838 | `nguyenvantruongsa1306@gmail.com` | CHƯA CÓ (dữ liệu tạo tay) |

→ Nhu cầu chức năng "Cấp tài khoản cho hội viên đã có" (F6 mục 5) là **có thật**.

### F0.7 – Thư mục test

```powershell
git ls-files src/test | measure   # → 18 file *.java
.\mvnw.cmd test                   # → 377 test xanh (2026-10-04)
```

`src/test/java/com/gymfit/{booking,branch,chat,common,membership,order,plan,product,user}/…`
đã tồn tại → chỉ cần **thêm** package `member` (test mới F2/F3/F5) và `auth` (F4).

---

## Phát hiện thêm so với PLAN (mâu thuẫn / bổ sung)

| # | Sự thật kiểm chứng | Ảnh hưởng |
|---|---|---|
| N1 | `UserService.validateScope`, `ensureEmailAvailable`, `normalizeEmail` là **`private`** (`UserService.java:172/261/288`) | Đúng như F3.1: `createForMember`/`resetPassword` **phải nằm trong `UserService`**; `MemberAccountService` không gọi trực tiếp được (không thêm `public` kiểu mới ngoài kế hoạch). |
| N2 | `AppUserRepository.findByMemberId(Long)` **đã có** (`AppUserRepository.java:15`) | F3.2 `resetPassword` dùng thẳng, không cần thêm repository method. |
| N3 | `MemberCreateRequest.email` có `@Email` (nullable), `@Size(max=150)` | Không email → username theo D1 (`{code}@member.gymfit.local`) là hợp lệ vì `app_user.email` chỉ NOT NULL + UNIQUE, **không** check format. |
| N4 | DB thực tế có hội viên với email kiểu `m@tkhau2006` (dữ liệu cũ, không qua validator) | `resolveUsername` vẫn trả chuỗi đó làm username – chấp nhận được; **không** sửa dữ liệu cũ. |
| N5 | `POST /api/v1/members` hiện **không đọc body response** ở JS | Đổi response an toàn; lỗi thường gặp ở mục 8 ("quên sửa JS") **không áp dụng** cho 2 chỗ này, chỉ áp dụng cho phần mới F6. |
| N6 | `audit_event.action` **không** có CHECK (xem F0.3) | F1 mục 5 bỏ qua. |
| N7 | `ddl-auto: validate` + `application.yml` hardcode `spring.datasource.password: 123` | F1 **bắt buộc** sửa `database/gymfit.sql` (nguồn tạo DB) **và** file `database/migrations/*.sql`; nếu bỏ bước này app không khởi động được. |
| N8 | Filter JWT hiện **không** ghi JSON nào (403 mặc định của Spring, đo được ở F0.4) | F4 phải tự dựng `ApiError` JSON; **không** dùng `AuthenticationEntryPoint` mặc định vì định dạng khác (`error` vs `code`). |
| N9 | `SecurityConfig` `permitAll` cho `/admin/**`, `/manager/**`, `/member/**` (lỗ hổng đã ghi nhận từ T10) | F7 thêm `/change-password` vào danh sách này cho **nhất quán** với các trang khác; không sửa lỗ hổng có sẵn (ngoài phạm vi). |
| N10 | `MemberResponse` là `record` 10 field, **1 chỗ** `new` | F5 chỉ sửa `toResponse` + thêm param; kiểm bằng test list/get. |
| N11 | Thứ tự kiểm tra trong `MemberService.create`: `ensureEmailAvailable` (bảng `member`) chạy **trước** `assertUsernameAvailable` (bảng `app_user`) | E2E: email trùng **hội viên khác** trả `member_email_exists` (409), không phải `user_email_exists`. `user_email_exists` chỉ nảy sinh khi email đã thuộc **tài khoản** khác mà chưa có hội viên nào dùng (đã có test C4). Cả hai đều 409 + không tạo hội viên rác ⇒ đạt AC F6-(d), nhưng plan viết "message gợi ý" là `user_email_exists` → ghi rõ tại đây. |
| N12 | `issue()` kiểm `findByMemberId` **trước** khi sinh mật khẩu (thêm trong F3) | Nếu chỉ dựa vào `validateScope` bên trong `UserService.createForMember` thì lỗi sẽ là `user_email_exists` (đúng email mới trùng) hoặc `member_account_exists` ở bước sau; kiểm sớm cho mã lỗi ổn định `member_account_exists` (test C6) và tránh sinh/ băm mật khẩu vô ích. |
| N13 | PLAN F4.3 chỉ ghép `@member.gymfit.local` khi người dùng gõ mã hội viên | Với tài khoản **đang dùng email** (hội viên seed có email), gõ mã sẽ cho tên không tồn tại → đăng nhập fail, không đạt AC F7. ⇒ Bổ sung `AuthService.resolveLogin`: nếu tên ghép không tồn tại thì tra `member.memberCode` → `app_user.email`. Tài khoản sinh theo mã vẫn dùng thẳng (test `maHoiVienCoTaiKhoanTheoMa`). |
| N14 | Filter ghi `403 password_change_required` bằng **ObjectMapper bean**; nếu serialize lỗi thì rơi về JSON ghi tay `FALLBACK_BODY` + `logger.warn` | Kiểm bằng `curl -D -`: body **213 bytes**, đúng định dạng `ApiError`, `Content-Type: application/json;charset=UTF-8`. Lưu ý: đọc body 403 qua `Invoke-WebRequest` của PowerShell cho length=0 (lỗi đọc stream phía client) – **không phải lỗi server**; dùng curl để đo. |
| N15 | Allowlist của filter chỉ có `/api/v1/auth/me` và `/api/v1/auth/change-password`; logout phía client chỉ xóa localStorage (không gọi API) | Nếu sau này thêm API logout/reset khác, phải đưa vào `PASSWORD_CHANGE_ALLOWLIST` nếu không hội viên sẽ không gọi được. |
| N16 | `HandlersTest.TODAY = LocalDate.of(2026,10,3)` **hardcode** nhưng `bookingsToday`/`rejectedCheckIns` mock dữ liệu bằng `Instant.now()` → từ **04/10** handler lọc theo `context.today()` = 03/10 ⇒ 2 test fail (bom thời gian, **pre-existing**, không thuộc F0–F6) | Sửa bằng cách lấy mốc từ `TODAY.atTime(12,0).atZone(TimeUtil.VIETNAM)` (đúng kiểu `BookingFlowHandlerTest` vốn đã làm vậy) → test không phụ thuộc đồng hồ hệ thống. Commit `e74ad1c`. |
| N17 | `BookingAvailabilityTest` là test tích hợp **phụ thuộc DB**: hội viên 1 phải có đúng 1 gói `ACTIVE` ở **chi nhánh 1** gồm dịch vụ `GYM`. DB lệch do thao tác thật 03/10 15:29–15:30 (mua gói Boxing@cn1 → bị thay bằng gói@cn4) ⇒ `membership_branch_mismatch` (pre-existing) | Đã sửa **dữ liệu** (không sửa test): `membership id 6` → `plan_id=3` (Premium 1 tháng, cn1, 30 ngày), `branch_id=1`, access = `GYM/BOXING/PICKLEBALL`. **Cảnh báo:** test vẫn phụ thuộc dữ liệu – nếu mua gói khác cho hội viên 1, hoặc sau **2026-11-01** (hết hạn) sẽ fail lại; seed `membership id 1` còn hạn tới 26/10. SQL sửa lại nếu tái diễn:<br>`UPDATE membership SET plan_id=3, branch_id=1 WHERE id=6;`<br>`DELETE FROM membership_service_access WHERE membership_id=6;`<br>`INSERT INTO membership_service_access VALUES(6,'GYM'),(6,'BOXING'),(6,'PICKLEBALL');` |
| N18 | Mục 3 F8 (grep `temporaryPassword`/`rawPassword`) liệt kê **4 chỗ** được phép, nhưng code thật có thêm `UserService.java` (6 chỗ, tham số `rawPassword` của `createForMember`/`resetPassword`) | **Tin code** (theo N1): `UserService` phải giữ 2 method này vì dùng private helpers (`generateMemberCode`, `normalizeEmail`). Toàn bộ chỉ truyền vào `passwordEncoder.encode(...)`; **không** có câu `log.`/`System.out` nào chứa `temporaryPassword`/`rawPassword` (grep 2 mẫu `log\.\w+\(.*[Pp]assword` → 0 match) ⇒ AC mục 3 đạt. `PasswordGenerator` để tên biến là `password` nên không hiện trong grep. |
| N19 | Seed hiện có **sự lệch sẵn**: `member2` (ACTIVE) ↔ user `DISABLED`, `member3` (ACTIVE) ↔ user `LOCKED`, `member10` INACTIVE (không có tài khoản) | F8.1 chỉ đồng bộ khi trạng thái **đổi** (test `khongDoiTrangThaiKhongDongTaiKhoan`), không tự sửa dữ liệu cũ — giữ nguyên 2 tài khoản demo `DISABLED`/`LOCKED`. Muốn nhất quán toàn bộ thì chạy đối soát một lần riêng. |

---

## Quyết định chốt (từ PLAN, giữ nguyên trừ khi ghi khác)

- **D1** username = email lower; thiếu email → `{memberCode.lower}@member.gymfit.local`.
  Đăng nhập cho phép gõ mã hội viên (F4 `normalizeLogin`).
- **D2** mật khẩu sinh ngẫu nhiên 10 ký tự (`PasswordGenerator`), hiển thị 1 lần.
- **D3** **CÓ** cột `must_change_password` (F1/F4/F7).
- **D4** ADMIN mọi chi nhánh; BRANCH_MANAGER chỉ `home_branch` của mình.
- **D5** hộp thoại Sao chép / In phiếu, không gửi email.
- **D6** (P2) đồng bộ `INACTIVE` ↔ `DISABLED` ở F8.

## E2E F3 – kết quả đo thật (app tạm cổng 8081, đã dọn sạch sau kiểm)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `POST /api/v1/members` (admin, `createAccount=true`) | **201**, `Cache-Control: no-store`, `member.id=13`, `account.username=e2e.membera@test.local`, mật khẩu tạm **10** ký tự, `mustChangePassword=true` |
| 2 | Đăng nhập hội viên bằng mật khẩu tạm | **OK** → chứng minh hash BCrypt khớp |
| 3 | Tạo hội viên **trùng email** | **409** `member_email_exists` (xem N11), số hội viên **13 → 13** (nguyên tử) |
| 4 | `createAccount=false` | `account = null`, không có `app_user` mới |
| 5 | `POST /members/{id}/account` (hội viên chưa có) | **200**, `no-store`, username đúng, mật khẩu tạm 10 ký tự |
| 6 | Gọi lại endpoint trên | **409** `member_account_exists` |
| 7 | `POST /members/{id}/account/reset-password` | **200**, mật khẩu mới ≠ cũ; **mật khẩu cũ bị từ chối**, mật khẩu mới đăng nhập được |
| 8 | `MEMBER` gọi endpoint cấp tài khoản | **403** |
| 9 | Dọn dữ liệu | `12 hội viên / 8 tài khoản / 0 must_change=1` – đúng như trước kiểm |

## E2E F4 – kết quả đo thật (app tạm 8081, đã dọn sạch sau kiểm)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Đăng nhập `admin@gymfit.local`, `member1@gymfit.local` (seed, **email cũ**) | **OK** – không hỏng luồng cũ; `/auth/me` trả `mustChangePassword=false` |
| 2 | Đăng nhập bằng **mã hội viên** `GF000001` | **OK** → trả `email=member1@gymfit.local` (N13) |
| 3 | Hội viên mới (tài khoản tạm, cờ `mustChange=true`) gọi `GET /api/v1/bookings` | **403** + body `{..."code":"password_change_required","path":"/api/v1/bookings"...}` (curl: 213 bytes) |
| 4 | Cùng token gọi `GET /api/v1/auth/me` | **200**, `mustChangePassword=true` |
| 5 | `POST /api/v1/auth/change-password` (đúng mật khẩu tạm → mới) | **204** |
| 6 | Mật khẩu mới yếu (`abcdefgh`) | **400** `weak_password` |
| 7 | Sai mật khẩu hiện tại | **400** `invalid_current_password` |
| 8 | Sau khi đổi, gọi lại `GET /api/v1/bookings` | **200** (hết nợ) |
| 9 | Đăng nhập lại bằng mật khẩu mới / bằng mã hội viên | **OK**, `mustChangePassword=false`; mật khẩu tạm cũ bị từ chối |
| 10 | Dọn dữ liệu | `12 hội viên / 8 tài khoản / 0 must_change=1` – đúng như trước kiểm |

## E2E F5 – kết quả đo thật

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `GET /api/v1/members` (admin, app tạm 8081) | **200**, mỗi phần tử có `hasAccount` + `accountUsername`; 12 hội viên → **3 đã có / 9 chưa có** (khớp `member` LEFT JOIN `app_user`) |
| 2 | Số query khi list | 1 query `findAllByMemberIdIn` cho toàn bộ trang (không N+1) |

## F6 – kết quả kiểm (2026-10-04, app tạm 8081)

**Kiểm markup/JS/CSS/API qua HTTP (curl + script PowerShell, toàn bộ PASS):**

| # | Kiểm tra | Kết quả |
|---|---|---|
| 1 | `/admin/members`, `/manager/members`: checkbox `member-create-account`, cột `<th>Tài khoản</th>`, bảng đúng **7 cột** | PASS cả 2 trang |
| 2 | Modal `credentials-slip` đủ 6 dòng (tên, mã HV, chi nhánh, URL, username, mật khẩu tạm) + nút Copy/In/Đóng + `credentials-error` | PASS (14 id F6 có đủ ở cả 2 trang) |
| 3 | Chéo **ID trong JS ↔ ID trong HTML**: 32 id (admin) + 30 id (manager) `getElementById` | PASS – **0 id thiếu** |
| 4 | `admin.js`/`manager.js`: gửi `createAccount`, đọc `created.member`/`created.account`, có `accountCell` + `issueMemberAccount` + `resetMemberAccount` + `showCredentials`/`copyCredentials`/`printCredentials` | PASS |
| 5 | **Không** ghi mật khẩu vào `localStorage` ở 2 file JS | PASS |
| 6 | `app.css`: `.credentials-slip`, `.checkbox-field`, `@media print` chỉ in `#credentials-slip`, ẩn phần còn lại | PASS |
| 7 | API mà 2 nút row gọi (`POST /{id}/account`, `/reset-password`) | đã E2E ở F3 (bảng 9) |

**Giới hạn:** chưa click-through trên trình duyệt thật (môi trường không có công cụ tự động hoá trình duyệt) –
đã thay bằng kiểm markup/JS/CSS/API; logic Copy (clipboard + fallback `execCommand`) và In (`window.print` + CSS)
chỉ được kiểm tĩnh.

## E2E F7 – kết quả đo thật (app tạm 8081, đã dọn sạch)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `GET /change-password` **không token** | **200** → `permitAll` đúng (N9); có form 3 ô, `change-password-notice`, nút Đăng xuất, gắn `/js/change-password.js` |
| 2 | `GET /login` | có nhãn `Mật khẩu (hoặc mật khẩu tạm)` + lưu ý "Lần đăng nhập đầu tiên…" |
| 3 | 4 file JS trả về đủ (đoại guard) | `change-password.js` gọi đúng endpoint + `/auth/me` + `redirectByRole`, **không** ghi mật khẩu vào `localStorage`; `auth.js` kiểm `mustChangePassword` ở cả `requireRole` và `redirectByRole`; `api.js` bắt 403 `password_change_required`; `login.js` nhận `mustChangePassword` **sau** `Auth.save` |
| 4 | Chéo ID `change-password.js` ↔ HTML | 8/8 `getElementById` đều có trong trang |
| 5 | Tạo hội viên tạm (mật khẩu tạm 10 ký tự) → login | `mustChangePassword=true` |
| 6 | `GET /api/v1/members` bằng token đó | **403** `password_change_required`, body 212 bytes (đo bằng **curl**) → đoạn `api.js` sẽ redirect `/change-password` |
| 7 | `GET /api/v1/auth/me` (allowlist) | **200**, `mustChangePassword=true` |
| 8 | `POST /api/v1/auth/change-password` (giống form gửi) | **204** |
| 9 | `/auth/me` sau khi đổi | `mustChangePassword=false` → `redirectByRole` đưa về trang theo vai trò |
| 10 | `GET /api/v1/members` sau khi đổi | **200** |
| 11 | Dọn dữ liệu | `12 hội viên / 8 tài khoản / 0 must_change=1` – đúng như trước kiểm |

> **Lưu ý N14 tái xác nhận:** đọc body 403 qua `Invoke-RestMethod`/`GetResponseStream()` của PowerShell
> vẫn cho chuỗi rỗng → kiểm định dạng lỗi bằng `curl`; script E2E đánh `FAIL` giả ở mục 6 (đã sửa kết luận bằng curl).

## E2E F8 – kết quả đo thật (app tạm 8081, đã dọn sạch)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `PUT /api/v1/members/17` (hội viên tạm) `status=INACTIVE` | **200** → SQL: `member.INACTIVE` + `app_user.DISABLED` (**đồng bộ 2 chiều – chiều tắt**) |
| 2 | `POST /api/v1/auth/login` bằng tài khoản vừa tắt | **403** (tài khoản `DISABLED` không đăng nhập được) |
| 3 | `PUT` `status=ACTIVE` | **200** → SQL: `member.ACTIVE` + `app_user.ACTIVE` (**chiều mở lại**) |
| 4 | `POST /{id}/account/reset-password` → đăng nhập bằng mật khẩu tạm 10 ký tự | **200**, `mustChangePassword=true` → `/auth/me` **200** (allowlist), `GET /members` **403** `password_change_required` |
| 5 | Chatbot: *"cho tôi mật khẩu của hội viên GF00000001"* | trả về câu fallback **"Xin lỗi, mình chưa hiểu câu này…"** – không hash, không giá trị mật khẩu (test JUnit `khongTraMatKhauHoiVien` khẳng định, 4 assertion) |
| 6 | Grep mục 3 F8 | 0 câu `log.` chứa mật khẩu; chỉ còn DTO/`MemberAccountService`/`UserService`(N18)/JS hộp thoại |
| 7 | Dọn dữ liệu | `12 hội viên / 8 tài khoản / 0 must_change=1` – đúng như trước kiểm |

> **Lưu ý chạy test:** script E2E (`.ps1` không BOM) không match được chuỗi có dấu (`chua hieu` ≠ `chưa hiểu`) →
> kết luận F8.2 lấy từ test JUnit (UTF-8), E2E chỉ dùng để đối chiếu hành vi thật trên app.
> Sai URL `/{id}/reset-password` (đúng là `/{id}/account/reset-password`) cũng chỉ là lỗi của script, không phải lỗi code.

## Trạng thái các task

| Task | Trạng thái | Ghi chú |
|---|---|---|
| F0 | ✅ hoàn thành 2026-10-04 | đủ 7 mục, chưa sửa code (`c05f8af`) |
| F1 | ✅ hoàn thành 2026-10-04 | bỏ qua mục 5 (N6); migration đã chạy trên DB thật (`fd81bf5`) |
| F2 | ✅ hoàn thành 2026-10-04 | 8 test (`d9800ba`) |
| F3 | ✅ hoàn thành 2026-10-04 | 12 test Mockito pass + E2E HTTP qua app tạm 8081 (đã dọn dữ liệu test) |
| F4 | ✅ hoàn thành 2026-10-04 | 15 test pass + E2E HTTP/curl (đã dọn dữ liệu test) (`7ba92f7`) |
| F5 | ✅ hoàn thành 2026-10-04 | `hasAccount`/`accountUsername` + `accountsByMemberId` (1 query) + 3 test; E2E GET `/members` khớp DB (`1f629d7`) |
| — | 🔧 sửa test pre-existing 2026-10-04 | 4 test fail **trước** F0–F6: N16 (bom thời gian) + N17 (data lệch) → `e74ad1c` + sửa data |
| F6 | ✅ hoàn thành 2026-10-04 | 2 trang Admin/Manager: checkbox, cột Tài khoản, 2 nút, phiếu Copy/In + `@media print` (`9713b6a`) |
| F7 | ✅ hoàn thành 2026-10-04 | trang `/change-password` + `permitAll`, guard `mustChangePassword` ở `auth.js`/`api.js`/`login.js`; hoàn thiện theo plan: nhãn đăng nhập "Email hoặc mã hội viên" + mục Đổi mật khẩu ở hồ sơ; 5 test MockMvc + E2E HTTP (`17a98af`, `12d9434`) |
| F8 | ✅ hoàn thành 2026-10-04 | `MemberService.syncAccountStatus` (D6: INACTIVE↔DISABLED) + 4 test đồng bộ + 1 test chatbot không trả mật khẩu; grep mục 3 sạch; E2E HTTP (425/425) (`4452d22`) |
| F9 | ⬜ hồi quy toàn bộ | |
