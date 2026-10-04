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

## Trạng thái các task

| Task | Trạng thái | Ghi chú |
|---|---|---|
| F0 | ✅ hoàn thành 2026-10-04 | đủ 7 mục, chưa sửa code (`c05f8af`) |
| F1 | ✅ hoàn thành 2026-10-04 | bỏ qua mục 5 (N6); migration đã chạy trên DB thật (`fd81bf5`) |
| F2 | ✅ hoàn thành 2026-10-04 | 8 test (`d9800ba`) |
| F3 | ✅ hoàn thành 2026-10-04 | 12 test Mockito pass + E2E HTTP qua app tạm 8081 (đã dọn dữ liệu test) |
| F4–F9 | ⬜ | |
