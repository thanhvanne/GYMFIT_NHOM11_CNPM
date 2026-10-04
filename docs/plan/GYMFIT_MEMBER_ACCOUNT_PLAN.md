# GYMFIT – PLAN: TỰ ĐỘNG CẤP TÀI KHOẢN + MẬT KHẨU KHI THÊM HỘI VIÊN

> Dành cho AI code. Làm tuần tự **F0 → F9**; mỗi task có file cần sửa, đặc tả, tiêu chí nghiệm thu (AC). Chưa đạt AC thì không sang task sau.
> "Học viên" trong yêu cầu = **hội viên** (`member`) trong code và giao diện.

---

## 0. QUY TẮC LÀM VIỆC

1. Không thêm dependency mới (không có thư viện gửi mail trong `pom.xml`; việc gửi email **ngoài phạm vi**). Dùng Spring Security `PasswordEncoder` (BCrypt cost 12) có sẵn.
2. **Mật khẩu thô** chỉ tồn tại trong bộ nhớ và trong response HTTP của đúng 3 endpoint ở mục 4. **Cấm** ghi vào log, audit (`details_json`), exception message, DB dạng rõ, localStorage/sessionStorage.
3. Tái sử dụng rule đã có (`UserService.validateScope`, `BranchScopeGuard`, `ConflictException`/`ForbiddenException`…). Không viết lại logic kiểm tra trùng email/hội viên.
4. Code mới theo phong cách hiện có: Lombok `@RequiredArgsConstructor`, DTO là `record`, thông báo lỗi tiếng Việt, `@Transactional` ở service.
5. Sau mỗi task: `mvn -q -DskipTests compile` phải pass → chạy test của task → gợi ý message commit `F3: ...`.
6. Plan mâu thuẫn code → **tin code**, ghi vào `docs/features/member-account/NOTES.md`.
7. Frontend: render bằng `textContent` (không `innerHTML`), theo đúng pattern modal/`show()`/`hide()`/`Api.post` đang dùng trong `admin/members.js`.

---

## 1. HIỆN TRẠNG ĐÃ XÁC MINH (từ code)

| # | Sự thật | Hệ quả cho thiết kế |
|---|---|---|
| 1 | `MemberService.create` chỉ lưu bảng `member` (mã `GF` + 8 số ngẫu nhiên). **Không tạo `app_user`.** | Cần thêm bước tạo tài khoản |
| 2 | Chỉ ADMIN tạo được tài khoản, qua `POST /api/v1/users` (`UserController` `@PreAuthorize ADMIN`), nhập tay mật khẩu (6–72 ký tự). Tài khoản MEMBER phải gắn `memberId` tồn tại, `ACTIVE`; mỗi hội viên 1 tài khoản (`member_account_exists`, chỉ mục `UX_app_user_member`) | Manager hiện **không** cấp được tài khoản → cần endpoint mới có kiểm tra chi nhánh |
| 3 | Đăng nhập bằng **email**: `LoginRequest` có `@Email`, `AuthService.login` chuẩn hóa chữ thường, `AppUserDetailsService` tìm `findByEmailIgnoreCase`, `login.html` `type="email"` | Tên đăng nhập hiện là email |
| 4 | `app_user.email` **NOT NULL, UNIQUE**; còn `member.email` **được phép NULL** | Hội viên không có email thì không có tên đăng nhập → cần quy tắc (D1) |
| 5 | **Không có** đổi mật khẩu, đặt lại mật khẩu, hay cờ "bắt buộc đổi mật khẩu" | Nếu nhân viên biết mật khẩu mãi mãi thì không an toàn → F1, F4, F7 |
| 6 | Mật khẩu băm BCrypt cost 12; `JwtAuthenticationFilter` nạp lại `AppUser` theo `userId` mỗi request | Có chỗ chặn sớm theo trạng thái/cờ đổi mật khẩu |
| 7 | `app_user` có `CK_app_user_scope`: role `MEMBER` ⇒ `member_id NOT NULL`, `branch_id NULL` | Tài khoản hội viên phải đúng ràng buộc này |
| 8 | `AuditService.record(actorUserId, action, entityType, entityId, branchId, Map details)` | Ghi audit, **không** đưa mật khẩu vào `details` |
| 9 | UI tạo hội viên: Admin `admin/members.js` và Manager `manager/members.js` cùng `POST /api/v1/members` (form `#member-form`) | Phải sửa **cả hai** |
| 10 | Seed có 10 hội viên nhưng chỉ vài tài khoản MEMBER (`member1–3@gymfit.local`) – **xác minh bằng lệnh đếm ở F0** | Cần chức năng "Cấp tài khoản cho hội viên đã có" |

---

## 2. CÁC QUYẾT ĐỊNH CẦN CHỐT (mặc định đề xuất, nhóm có thể đổi)

| Mã | Câu hỏi | Mặc định |
|---|---|---|
| **D1** | Tên đăng nhập là gì? | Email của hội viên (chuẩn hóa chữ thường). Nếu **không có email**: `{memberCode viết thường}@member.gymfit.local` (chỉ là định danh, không phải hộp thư thật). Cho phép gõ **mã hội viên** ở ô đăng nhập (F4) |
| **D2** | Mật khẩu từ đâu? | Hệ thống **sinh ngẫu nhiên 10 ký tự**, hiển thị **một lần** cho nhân viên |
| **D3** | Có bắt đổi mật khẩu lần đầu? | **Có** (thêm cột `must_change_password`, sửa DB). Nếu nhóm không muốn đổi DB: bỏ F1/F4/F7 và ghi rủi ro vào báo cáo |
| **D4** | Ai được cấp/đặt lại? | ADMIN (mọi chi nhánh), BRANCH_MANAGER (chỉ hội viên `home_branch` của mình) |
| **D5** | Giao thông tin cho hội viên thế nào? | Hộp thoại "Thông tin đăng nhập": **Sao chép** + **In phiếu**. Không gửi email |
| **D6** | Khi hội viên chuyển `INACTIVE`, tài khoản thì sao? | (P2, F8) tự khóa tài khoản; trở lại `ACTIVE` thì mở lại |

---

## 3. THIẾT KẾ TỔNG QUAN

```
[Admin/Manager] mở form "Thêm hội viên" ──☑ Cấp tài khoản đăng nhập (mặc định bật)
        │ POST /api/v1/members {…, createAccount:true}
        ▼
MemberController → MemberService.create (@Transactional)
   1. kiểm quyền/chi nhánh/SĐT/email (đã có)
   2. sinh memberCode (đã có)
   3. MemberAccountService.resolveUsername(...) + kiểm trùng → lỗi thì DỪNG (chưa lưu gì)
   4. lưu member
   5. MemberAccountService.issue(...) → PasswordGenerator → BCrypt → lưu app_user(MEMBER, mustChange=true)
   6. trả { member, account:{username, temporaryPassword, mustChangePassword} }   (Cache-Control: no-store)
        ▼
UI: hộp thoại thông tin đăng nhập (copy / in phiếu) — mật khẩu hiển thị MỘT lần
        ▼
Hội viên đăng nhập → bị chuyển sang trang Đổi mật khẩu → đặt mật khẩu mới → vào hệ thống
```

**Tính nguyên tử:** tạo hội viên + tài khoản trong **một transaction**; lỗi ở bước tạo tài khoản thì hội viên **không** được lưu.

---

## 4. HỢP ĐỒNG API (cố định)

| Method & path | Quyền | Body / Kết quả |
|---|---|---|
| `POST /api/v1/members` *(đổi response)* | ADMIN, BRANCH_MANAGER (giữ nguyên như hiện có) | Request thêm `Boolean createAccount` (null ⇒ `true`). Response: `MemberCreateResponse` |
| `POST /api/v1/members/{id}/account` *(mới)* | ADMIN, BRANCH_MANAGER (đúng chi nhánh) | không body → `AccountCredentialsResponse` |
| `POST /api/v1/members/{id}/account/reset-password` *(mới)* | như trên | không body → `AccountCredentialsResponse` |
| `POST /api/v1/auth/change-password` *(mới)* | đã đăng nhập | `{currentPassword, newPassword}` → `204` |

```java
public record AccountCredentialsResponse(Long userId, String username,
        String temporaryPassword, boolean mustChangePassword) {}
public record MemberCreateResponse(MemberResponse member, AccountCredentialsResponse account) {} // account=null nếu createAccount=false
public record ChangePasswordRequest(@NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
```
- `MemberResponse` thêm 2 field: `boolean hasAccount`, `String accountUsername` (F5).
- `CurrentUserResponse` (và `LoginResponse.user`) thêm `boolean mustChangePassword` (F4).
- Ba endpoint trả mật khẩu phải kèm header `Cache-Control: no-store` (dùng `ResponseEntity`).

**Mã lỗi (message tiếng Việt):**

| code | HTTP | message gợi ý |
|---|---|---|
| `member_account_exists` | 409 | Hội viên đã có tài khoản đăng nhập *(đã có trong `UserService`)* |
| `user_email_exists` | 409 | Email tài khoản đã được sử dụng *(đã có)* |
| `member_inactive` | 409 | Không thể tạo tài khoản cho hội viên không hoạt động *(đã có)* |
| `account_not_found` | 404 | Hội viên chưa có tài khoản đăng nhập |
| `invalid_current_password` | 400 | Mật khẩu hiện tại không đúng |
| `weak_password` | 400 | Mật khẩu mới cần ≥ 8 ký tự, gồm chữ và số |
| `password_unchanged` | 400 | Mật khẩu mới phải khác mật khẩu hiện tại |
| `password_change_required` | 403 | Bạn cần đổi mật khẩu trước khi sử dụng hệ thống |

---

## 5. CÁC TASK

### F0 – Chuẩn bị & xác minh (0.5 ngày)
Tạo `docs/features/member-account/NOTES.md`, ghi kết quả (kèm lệnh) cho:
1. Mọi nơi dùng `POST /api/v1/members` và `MemberResponse`: `grep -rn "/api/v1/members" src/main/resources/static/js`; `grep -rn "new MemberResponse" src` (bắt buộc cập nhật tất cả khi đổi record).
2. `MemberController`: `@PreAuthorize`, mã HTTP của `create`.
3. `database/gymfit.sql`: bảng `audit_event` có `CHECK` giới hạn cột `action` không? (nếu có phải thêm giá trị mới).
4. `JwtAuthenticationFilter`: cách hiện tại xử lý token sai/user bị khóa và cách ghi JSON lỗi (copy đúng định dạng `ApiError` của `GlobalExceptionHandler`).
5. `static/js/core/auth.js` (`Auth.save`, `redirectByRole`, kiểm tra vai trò từng trang) và `UiController` (cách khai báo route trang `/login`).
6. Đếm số hội viên chưa có tài khoản trong seed: `SELECT` tương đương trên `INSERT` của `gymfit.sql`.
7. `ls src/test` (thư mục test có tồn tại chưa; nếu chưa, tạo `src/test/java/com/gymfit/...`).

**AC:** file NOTES có đủ 7 mục; **chưa sửa code**.

---

### F1 – Cơ sở dữ liệu & entity (0.5 ngày) *(chỉ khi D3 = có)*
**File:** `database/gymfit.sql`, `database/migrations/2026_add_must_change_password.sql` (mới), `user/AppUser.java`, `common/security/AppPrincipal.java`.

1. Trong `CREATE TABLE app_user` thêm cột:
```sql
must_change_password BIT NOT NULL
    CONSTRAINT DF_app_user_must_change_password DEFAULT 0,
```
2. File migration cho DB đã tạo sẵn:
```sql
IF COL_LENGTH('app_user','must_change_password') IS NULL
    ALTER TABLE app_user ADD must_change_password BIT NOT NULL
        CONSTRAINT DF_app_user_must_change_password DEFAULT 0;
GO
```
3. `AppUser`: `@Column(name = "must_change_password", nullable = false) private boolean mustChangePassword;`
4. `AppPrincipal`: thêm field `mustChangePassword` lấy từ `AppUser`, có getter.
5. Nếu F0 cho thấy `audit_event.action` có `CHECK`: thêm `PASSWORD_RESET`, `PASSWORD_CHANGED`.

**AC:** DB tạo lại từ `gymfit.sql` rồi app khởi động (`ddl-auto: validate` không lỗi); tài khoản seed cũ có `must_change_password = 0`.

---

### F2 – Sinh mật khẩu & quy tắc tên đăng nhập (0.5 ngày)
**File:** `common/util/PasswordGenerator.java`, `member/MemberAccountService.java` (khung), tests.

```java
public final class PasswordGenerator {
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // bỏ I, O
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz"; // bỏ l, o
    private static final String DIGIT = "23456789";                 // bỏ 0, 1
    private static final SecureRandom RND = new SecureRandom();
    public static String generate() { /* 10 ký tự: ≥1 hoa, ≥1 thường, ≥1 số; trộn Fisher–Yates bằng SecureRandom */ }
}
```
`MemberAccountService.resolveUsername(String memberEmail, String memberCode)`:
- email không rỗng → `email.trim().toLowerCase(Locale.ROOT)`;
- ngược lại → `memberCode.toLowerCase(Locale.ROOT) + "@member.gymfit.local"`.
Hằng số `LOGIN_DOMAIN = "member.gymfit.local"` (public static, dùng lại ở F4).

**Test:** `PasswordGeneratorTest` (độ dài 10; đủ nhóm ký tự; không chứa `0 O 1 l I`; 2.000 lần sinh không trùng nhau quá 1 cặp), `UsernameResolveTest` (có email → lowercase; không email → `gf00000007@member.gymfit.local`).
**AC:** test pass.

---

### F3 – Backend lõi: cấp tài khoản (2 ngày)
**File:** `user/UserService.java`, `member/MemberAccountService.java`, `member/MemberService.java`, `member/MemberController.java`, `member/dto/*` (3 record mới + sửa `MemberCreateRequest`).

**3.1 `UserService`** – thêm (tái dùng `validateScope`, `ensureEmailAvailable`, `normalizeEmail` có sẵn):
```java
@Transactional
public AppUser createForMember(Long actorUserId, Member member, String username, String rawPassword) {
    ensureEmailAvailable(username, null);                       // user_email_exists
    Scope scope = validateScope(RoleCode.MEMBER, null, member.getId(), null); // member_inactive / member_account_exists
    AppUser user = AppUser.builder()
        .fullName(member.getFullName()).email(username)
        .passwordHash(passwordEncoder.encode(rawPassword))
        .roleCode(RoleCode.MEMBER).status(UserStatus.ACTIVE)
        .branchId(null).memberId(scope.memberId())
        .mustChangePassword(true)                                // bỏ dòng này nếu D3 = không
        .createdAtUtc(TimeUtil.now()).updatedAtUtc(TimeUtil.now()).build();
    AppUser saved = userRepository.save(user);
    auditService.record(actorUserId, "USER_CREATED", "APP_USER", saved.getId(), member.getHomeBranchId(),
        Map.of("email", saved.getEmail(), "role", "MEMBER"));      // KHÔNG có mật khẩu
    return saved;
}
@Transactional
public void resetPassword(Long actorUserId, AppUser user, String rawPassword, Long branchId) { /* encode, mustChange=true, updatedAt, save, audit "PASSWORD_RESET" {email} */ }
```
> Lưu ý: `createForMember` được gọi **sau** khi member đã `save` (cần `member.getId()`); phần kiểm tra sớm xem 3.3.

**3.2 `MemberAccountService`** (`@Service`, dùng `UserService`, `AppUserRepository`, `BranchScopeGuard`):
- `String assertUsernameAvailable(String email, String memberCode)` → gọi `resolveUsername`, nếu `findByEmailIgnoreCase` có kết quả ⇒ `ConflictException("user_email_exists", ...)`; trả username.
- `AccountCredentialsResponse issue(AppPrincipal actor, Member member)`: nếu actor là MANAGER ⇒ `branchScopeGuard.requireBranch(actor, member.getHomeBranchId())`; `raw = PasswordGenerator.generate()`; gọi `createForMember`; trả credentials (`mustChangePassword` theo D3).
- `AccountCredentialsResponse resetPassword(AppPrincipal actor, Member member)`: tìm user `findByMemberId` (không có ⇒ `NotFoundException("account_not_found", …)`); kiểm chi nhánh như trên; sinh mật khẩu; gọi `userService.resetPassword`.

**3.3 `MemberService.create`** – sửa:
```java
boolean wantAccount = request.createAccount() == null || request.createAccount();
String memberCode = generateMemberCode();
if (wantAccount) memberAccountService.assertUsernameAvailable(request.email(), memberCode); // trước khi lưu
Member saved = memberRepository.save(member /* dùng memberCode ở trên */);
auditService.record(... "MEMBER_CREATED" ...);          // giữ nguyên
AccountCredentialsResponse account = wantAccount ? memberAccountService.issue(principal, saved) : null;
return new MemberCreateResponse(toResponse(saved), account);
```
Đổi kiểu trả về của `create` (và `MemberController.create`) thành `MemberCreateResponse`, thêm header `Cache-Control: no-store`.

**3.4 `MemberController`** – thêm hai endpoint ở mục 4, `@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")`, trả `ResponseEntity` kèm `no-store`.

**Test (`MemberAccountServiceTest`, `MemberServiceCreateTest`, Mockito, không DB):**
1. `createAccount=true` + có email → có đúng 1 `AppUser` được lưu: role `MEMBER`, `memberId` = id hội viên, `username` = email chữ thường, `passwordHash` ≠ mật khẩu thô và `BCrypt matches` đúng, `mustChangePassword = true`; response có `temporaryPassword` dài 10.
2. Không có email → username dạng `gf........@member.gymfit.local`.
3. `createAccount=false` → không lưu `AppUser`, `account == null`.
4. Email đã thuộc `app_user` khác → `ConflictException(user_email_exists)` và `memberRepository.save` **không** được gọi.
5. MANAGER tạo cho chi nhánh khác → `ForbiddenException` (hành vi cũ) và không tạo user.
6. `issue` cho hội viên đã có tài khoản → `member_account_exists`.
7. **Audit không rò mật khẩu:** `ArgumentCaptor` bắt mọi `auditService.record(...)`; không giá trị nào trong `details` chứa mật khẩu thô.
8. `resetPassword`: user không tồn tại → `account_not_found`; thành công → hash đổi, `mustChange=true`.

**AC:** 8 test pass; compile pass; (nếu có DB) tạo hội viên bằng Postman/Swagger trả đúng `account`.

---

### F4 – Đổi mật khẩu, đăng nhập bằng mã hội viên, ép đổi mật khẩu (1.5 ngày) *(D3 = có)*
**File:** `auth/AuthController.java`, `auth/AuthService.java`, `auth/dto/LoginRequest.java`, `auth/dto/CurrentUserResponse.java`, `auth/dto/ChangePasswordRequest.java`, `common/security/JwtAuthenticationFilter.java`.

1. **Đổi mật khẩu** `AuthService.changePassword(AppPrincipal p, ChangePasswordRequest r)`:
   - nạp `AppUser` theo `p.getUserId()`; `passwordEncoder.matches(current, hash)` sai ⇒ `invalid_current_password`;
   - mới == hiện tại ⇒ `password_unchanged`; mới không có chữ **và** số, hoặc trùng `username` ⇒ `weak_password`;
   - lưu hash mới, `mustChangePassword=false`, `updatedAtUtc`; audit `PASSWORD_CHANGED` `{email}`.
   - `AuthController`: `POST /change-password` → `204` (lấy principal qua `SecurityContextService` như các controller khác).
2. **`mustChangePassword` trong response:** thêm vào `CurrentUserResponse` và nơi tạo nó (`AuthService.toCurrentUser`).
3. **Đăng nhập bằng mã hội viên:** trong `AuthService.login`, thêm hàm
```java
private String normalizeLogin(String input) {
    String v = input.trim().toLowerCase(Locale.ROOT);
    return v.contains("@") ? v : v + "@" + MemberAccountService.LOGIN_DOMAIN;
}
```
   dùng `normalizeLogin` thay cho `trim().toLowerCase()` hiện có (cả lúc `authenticate` lẫn `findByEmailIgnoreCase`). `LoginRequest.email`: bỏ `@Email`, giữ `@NotBlank` (giữ tên field `email` để không vỡ client).
4. **Ép đổi mật khẩu phía server:** trong `JwtAuthenticationFilter`, sau khi nạp user hợp lệ, nếu `user.isMustChangePassword()` và đường dẫn **không** thuộc danh sách cho phép (`/api/v1/auth/me`, `/api/v1/auth/change-password`) ⇒ trả `403` JSON `{code:"password_change_required", ...}` theo đúng định dạng lỗi hiện có (đã ghi ở F0); **không** áp dụng cho đường dẫn trang (`/member/**`, `/login`, `/css/**`…) vì chúng `permitAll`.

**Test:**
- `AuthServiceChangePasswordTest`: sai mật khẩu hiện tại; trùng mật khẩu cũ; yếu (`abcdefgh`, `12345678`); thành công (BCrypt khớp, cờ về `false`, audit không chứa mật khẩu).
- `LoginIdentifierTest`: `"GF00000007"` ⇒ `gf00000007@member.gymfit.local`; `"A@B.com "` ⇒ `a@b.com`.
- `JwtFilterMustChangeTest` (`MockHttpServletRequest/Response`): user `mustChange=true` gọi `/api/v1/bookings` ⇒ 403 `password_change_required`; gọi `/api/v1/auth/change-password` và `/api/v1/auth/me` ⇒ cho qua; user `mustChange=false` ⇒ cho qua mọi đường dẫn.

**AC:** test pass; đăng nhập bằng email cũ của các tài khoản seed vẫn hoạt động (kiểm tay 1 lần).

---

### F5 – Hiển thị trạng thái tài khoản của hội viên (0.5–1 ngày) *(P1)*
**File:** `user/AppUserRepository.java`, `member/dto/MemberResponse.java`, `member/MemberService.java`.
1. `AppUserRepository`: `List<AppUser> findAllByMemberIdIn(Collection<Long> memberIds);`
2. `MemberResponse` thêm `boolean hasAccount, String accountUsername`; sửa **mọi** nơi `new MemberResponse(...)` (danh sách tìm ở F0).
3. `MemberService.list/get`: lấy một lần danh sách user theo `memberIds` (tránh N+1), dựng `Map<memberId, username>`; `toResponse(member, accounts)`.

**Test:** `MemberServiceListTest` – 3 hội viên, 1 có tài khoản ⇒ `hasAccount` đúng, `accountUsername` đúng, repository tài khoản chỉ gọi **1 lần**.
**AC:** test pass; JSON `GET /api/v1/members` có hai field mới.

---

### F6 – Giao diện Admin + Manager: thêm hội viên & hộp thoại thông tin đăng nhập (2 ngày)
**File:** `templates/admin/members.html`, `templates/manager/members.html`, `static/js/admin/members.js`, `static/js/manager/members.js`, `static/css/app.css`.

1. **Form thêm hội viên** (chỉ ở chế độ thêm, ẩn khi sửa): checkbox `#member-create-account` mặc định bật, nhãn "Cấp tài khoản đăng nhập cho hội viên", ghi chú: "Email dùng làm tên đăng nhập. Nếu để trống email, hệ thống tạo tên đăng nhập theo mã hội viên."
2. **Gửi request:** thêm `createAccount: checkbox.checked` vào body; đọc kết quả mới `data.member` / `data.account` (cập nhật mọi chỗ đang đọc response cũ).
3. **Hộp thoại `#credentials-modal`** (theo pattern modal hiện có): hiện khi `account != null`:
   - Họ tên, mã hội viên, chi nhánh, **Tên đăng nhập**, **Mật khẩu tạm** (font monospace), liên kết đăng nhập `location.origin + "/login"`;
   - cảnh báo: "Mật khẩu chỉ hiển thị một lần. Hãy đưa cho hội viên ngay và yêu cầu đổi mật khẩu ở lần đăng nhập đầu";
   - nút **Sao chép** (`navigator.clipboard.writeText`, có phương án dự phòng `document.execCommand("copy")` trên một `<textarea>` ẩn), nút **In phiếu**, nút **Đóng**;
   - khi đóng: xóa `textContent` của ô mật khẩu và biến giữ mật khẩu (đặt `null`).
4. **In phiếu:** thêm vào `app.css`:
```css
@media print {
  body * { visibility: hidden; }
  #credentials-slip, #credentials-slip * { visibility: visible; }
  #credentials-slip { position: absolute; left: 0; top: 0; width: 100%; }
}
```
   `#credentials-slip` chứa logo/tên GYMFIT, họ tên, mã hội viên, chi nhánh, URL đăng nhập, tên đăng nhập, mật khẩu, dòng nhắc đổi mật khẩu.
5. **Bảng danh sách (cần F5):** thêm cột/huy hiệu "Tài khoản" ("Đã có" / "Chưa có") và nút theo hàng: **Cấp tài khoản** (khi chưa có) gọi `POST /members/{id}/account`; **Đặt lại mật khẩu** (khi đã có) gọi `.../reset-password` có hộp xác nhận. Cả hai mở lại `#credentials-modal`.
6. Xử lý lỗi: hiển thị `message` từ API vào `#member-form-error`/`#page-error` (ví dụ `user_email_exists`).

**AC (kiểm tay, ghi ảnh chụp):** (a) thêm hội viên có email ⇒ hiện hộp thoại, đăng nhập được bằng thông tin đó; (b) thêm hội viên không email ⇒ username dạng `gf…@member.gymfit.local`; (c) bỏ tick ⇒ không hiện hộp thoại, không có tài khoản; (d) email trùng tài khoản khác ⇒ báo lỗi, **không** có hội viên mới trong danh sách; (e) Manager chi nhánh A không cấp/đặt lại được cho hội viên chi nhánh B; (f) sao chép/in hoạt động; (g) đóng hộp thoại rồi mở lại danh sách không còn thấy mật khẩu ở đâu (DOM, localStorage).

---

### F7 – Giao diện đăng nhập, trang đổi mật khẩu, hồ sơ hội viên (1.5 ngày) *(D3 = có)*
**File:** `templates/login.html`, `templates/change-password.html` (mới), `static/js/core/auth.js`, `static/js/core/change-password.js` (mới), `ui/UiController.java`, `common/security/SecurityConfig.java`, `templates/member/profile.html` + `static/js/member/profile.js`.

1. `login.html`: ô tài khoản `type="text"`, nhãn "Email hoặc mã hội viên", `autocomplete="username"`.
2. `UiController`: route `GET /change-password` (mẫu như `/login`); thêm `"/change-password"` vào danh sách `permitAll` của `SecurityConfig` (các trang đều `permitAll`, bảo vệ nằm ở JWT phía client + API).
3. `auth.js`: sau đăng nhập, nếu `user.mustChangePassword` ⇒ chuyển `/change-password` (thay cho `redirectByRole`); mọi trang đã đăng nhập: nếu thông tin người dùng đang lưu có `mustChangePassword = true` ⇒ chuyển về `/change-password`. Khi đổi mật khẩu xong: cập nhật cờ, rồi `redirectByRole`.
4. `change-password.html/js`: 3 ô (mật khẩu hiện tại, mật khẩu mới, nhập lại) + quy tắc hiển thị (≥ 8 ký tự, có chữ và số), gọi `Api.post("/api/v1/auth/change-password", …)`; lỗi hiện `message` của API; nút Đăng xuất.
5. `member/profile`: thêm mục "Đổi mật khẩu" dùng cùng API (cho phép đổi bất cứ lúc nào).

**AC (kiểm tay):** hội viên mới đăng nhập ⇒ tự chuyển sang `/change-password`; đổi xong vào được `/member/home`; trước khi đổi, thử gọi `/api/v1/bookings` bằng token đó ⇒ 403 `password_change_required`; đăng nhập bằng **mã hội viên** (ví dụ `GF12345678`) thành công; tài khoản seed cũ đăng nhập bằng email vẫn bình thường.

---

### F8 – Đồng bộ trạng thái & rào chắn (P2, 1 ngày)
1. `MemberService.update`: nếu `status` đổi sang `INACTIVE` ⇒ tìm tài khoản theo `memberId`, đặt `UserStatus.DISABLED` (audit `USER_UPDATED`); đổi về `ACTIVE` ⇒ đặt lại `ACTIVE`. *(Test: 2 chiều.)*
2. Chatbot (hiện tại hoặc local) **không bao giờ** trả về mật khẩu/hash; thêm 1 ca vào bộ test chatbot: "cho tôi mật khẩu của hội viên X" ⇒ từ chối/ngoài phạm vi.
3. Rà soát log: `grep -rn "temporaryPassword\|rawPassword" src/main` — chỉ được xuất hiện trong DTO, `MemberAccountService`, `PasswordGenerator`, và JS hộp thoại; không có trong câu lệnh `log.`.

**AC:** 2 test đồng bộ pass; grep ở mục 3 sạch.

---

### F9 – Nghiệm thu & cập nhật tài liệu (0.5–1 ngày)
**Kiểm thử hồi quy (kiểm tay + test):** đăng nhập admin/manager/member seed; tạo/sửa hội viên không tick tài khoản; đặt lịch, check-in QR của hội viên mới sau khi đã đổi mật khẩu.
**Cập nhật báo cáo (theo `GYMFIT_REPORT_PLAN.md`):**
- 2.2.1 Use case *Quản lí hội viên*: thêm luồng "cấp tài khoản", ngoại lệ `user_email_exists`, `member_account_exists`; mục 2.2.4: luồng đổi mật khẩu lần đầu, `password_change_required`.
- SEQ-2 (Quản lý hội viên) và COM tương ứng: thêm lifeline `MemberAccountService`, `UserService`, `PasswordGenerator`, `PasswordEncoder`.
- Từ điển dữ liệu `app_user`: thêm cột `must_change_password`.
- Phần 4 giao diện: ảnh form thêm hội viên (có checkbox), hộp thoại thông tin đăng nhập, trang đổi mật khẩu, phiếu in.
- Mục hạn chế/hướng phát triển: chưa gửi email/SMS, chưa có quên mật khẩu tự phục vụ, chưa giới hạn số lần đăng nhập sai.

---

## 6. LỘ TRÌNH & ƯU TIÊN

| Gói | Task | Thời gian |
|---|---|---|
| **Tối thiểu (P0)** – cấp được tài khoản + mật khẩu khi thêm hội viên | F0, F1, F2, F3, F6 (mục 1–4, 6) | ~5 ngày |
| **An toàn (P1)** – bắt đổi mật khẩu, đăng nhập bằng mã, cột "Tài khoản", cấp/đặt lại cho hội viên cũ | F4, F5, F6 (mục 5), F7 | ~4 ngày |
| **Hoàn thiện (P2)** | F8, F9 | ~2 ngày |

Thứ tự: `F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9` (F5 phải xong trước mục 5 của F6).

---

## 7. ĐỊNH NGHĨA HOÀN THÀNH

| Tiêu chí | Cách đo |
|---|---|
| Thêm hội viên (tick) tạo đúng 1 tài khoản MEMBER, gắn đúng `member_id`, đăng nhập được | kiểm tay + `MemberServiceCreateTest` |
| Tạo hội viên + tài khoản **nguyên tử** (lỗi ⇒ không còn hội viên rác) | test ca 4 (F3) |
| Mật khẩu thô **không** có trong DB, log, audit, `localStorage` | test ca 7 (F3) + `grep` (F8) + kiểm DOM |
| Manager không cấp/đặt lại ngoài chi nhánh | test ca 5 (F3) + kiểm tay (F6-e) |
| Hội viên mới bị ép đổi mật khẩu; API bị chặn tới khi đổi | `JwtFilterMustChangeTest` + kiểm tay (F7) |
| Tài khoản seed cũ và luồng hiện có (đặt lịch, check-in, mua gói) không hỏng | hồi quy F9 |
| `mvn -q test` xanh; `mvn -q -DskipTests compile` pass | CI cục bộ |

---

## 8. LỖI THƯỜNG GẶP — TRÁNH

- Đổi kiểu trả về của `POST /api/v1/members` mà quên sửa JS ở **cả Admin và Manager** (và mọi nơi khác trong `grep` ở F0).
- Đổi `record MemberResponse` nhưng bỏ sót một chỗ `new MemberResponse(...)` ⇒ lỗi biên dịch.
- Kiểm trùng email **sau** khi đã lưu hội viên (vẫn rollback được nhưng báo lỗi/ghi log khó hiểu) — kiểm tra **trước** khi lưu.
- Ghi mật khẩu vào `Map.of(...)` của audit hoặc vào `log.info`.
- Quên `Cache-Control: no-store` ở ba endpoint trả mật khẩu.
- Quên cập nhật `database/gymfit.sql` (ứng dụng `ddl-auto: validate` sẽ không khởi động nếu thiếu cột `must_change_password`).
- Để `LoginRequest.email` còn `@Email` ⇒ gõ mã hội viên bị từ chối ở bước validate.
- Bộ lọc JWT chặn luôn cả `/api/v1/auth/change-password` ⇒ hội viên không thể đổi mật khẩu (phải có allowlist).
- Quên tick mặc định bật ở form thêm; hoặc hiện checkbox khi đang **sửa** hội viên.

---

## 9. PROMPT GIAO VIỆC MẪU (dùng cho từng task)

```text
Đọc AGENTS.md (nếu có) và docs/features/member-account/PLAN.md: mục 0, 1, 4 và phần của task {Fn}.
Làm DUY NHẤT task {Fn}, chỉ tạo/sửa các file nêu trong {Fn}.
Thứ tự: (1) code, (2) test đúng danh sách của {Fn}, (3) chạy `mvn -q -DskipTests compile` rồi `mvn -q test -Dtest=<tên test>`, (4) đối chiếu từng AC và báo đạt/chưa đạt.
Nếu AC chưa đạt: sửa tối đa 2 vòng rồi dừng và báo nguyên nhân. Không thêm dependency. Không in mật khẩu ra log.
Cuối phiên trả lời: [Đã làm] · [File tạo/sửa] · [Lệnh đã chạy + kết quả] · [Điểm chưa chắc chắn] · [Việc tiếp theo].
```
