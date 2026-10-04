# LABELING — Quy ước gán nhãn dữ liệu chatbot GYMFIT

> Áp dụng cho **holdout**, **dataset huấn luyện**, **log thật** và **kịch bản**.
> Nguồn: mục **6.4** của `docs/plan/GYMFIT_CHATBOT_V2_EXPANSION_PLAN.md`.
> Trạng thái: đang dùng cho **holdout v2** (V2-2); dataset lớn hơn ở **V2-14**.

---

## 1. Quy ước gán nhãn (mục 6.4 — bắt buộc)

1. Câu **"chào/cảm ơn + yêu cầu"** → gán nhãn của **yêu cầu**,
   *không* phải nhãn chào.
   Ví dụ: `"xin chào, tôi cần hỏi về gói tập"` → `LIST_PLANS`
   (không phải `GREETING`).
2. Câu chứa **2 yêu cầu** → nhãn của **yêu cầu đầu**; yêu cầu thứ hai ghi vào
   trường `secondary` (dùng làm dữ liệu test cho `Segmenter` — V2-4).
3. Câu hỏi **"cách làm / quy định"** → `FAQ_GENERAL`;
   câu hỏi cần **dữ liệu của tôi** → intent dữ liệu
   (`MY_BOOKINGS`, `MY_MEMBERSHIP`, `MY_ORDERS`…).
   > Hiện `FAQ_GENERAL` **chưa tồn tại** (thêm ở V2-3); đến khi đó 6 intent
   > `FAQ_*` cũ vẫn giữ nhãn riêng, `intent-aliases.json` sẽ ánh xạ về
   > `FAQ_GENERAL` lúc huấn luyện lại.
4. Câu nhờ **chuyên môn** (bài tập, dinh dưỡng, y tế) → `OUT_OF_SCOPE`
   (subtype `ADVICE`).
5. **Không chắc** → loại khỏi tập, ghi vào `ambiguous.jsonl` kèm `reason`
   để cả nhóm thảo luận. **Không được đoán bừa.**
6. Hai người gán nhãn **độc lập** cho ≥ 100 câu → đo đồng thuận
   (**Cohen's κ ≥ 0,8**) trước khi chốt.

---

## 2. Schema dòng `holdout_v2.jsonl`

```json
{"text":"gói của tôi còn bao nhiêu ngày nữa",
 "intent":"MY_MEMBERSHIP",
 "tier":"EASY",
 "tags":["short","question"],
 "role":"MEMBER",
 "group":"H027"}
```

| Trường | Ý nghĩa | Bắt buộc |
|---|---|---|
| `text` | câu gõ thô, giữ nguyên dấu/teencode | ✔ |
| `intent` | nhãn thật (`Intent.name()`) | ✔ |
| `tier` | `EASY` / `NATURAL` / `ADVERSARIAL` | ✔ (V2) |
| `tags` | tag đo được, mảng rỗng được | ✔ (V2) |
| `role` | persona nói ra câu này: `MEMBER` / `STAFF` / `ADMIN` | ✔ (V2) |
| `group` | mã nguồn để lần lại câu gốc (`H001`…) | giữ lại từ V1 |

`ambiguous.jsonl` dùng schema: `{"text","intent","group","reason"}` —
`intent` ở đây là **nhãn gốc đã bị nghi ngờ**, không phải nhãn chốt.

---

## 3. Quy tắc gán `tier` (tự động, chưa chấm tay)

| Tier | Quy tắc (code: `HoldoutMigrator.tier`) |
|---|---|
| `ADVERSARIAL` | Toàn ký tự lạ/emoji **hoặc** có lỗi gõ trong danh sách (`cảu`, `dc`…) **hoặc** **mất dấu hoàn toàn** (không còn chữ nào ngoài ASCII) với ≥ 2 từ |
| `EASY` | ≤ 6 từ **và** không có `?` **và** ≤ 1 dấu phẩy — dạng từ khóa/mẫu viết theo mẫu |
| `NATURAL` | còn lại: câu hỏi đầy đủ, câu dài, câu ghép |

> ⚠️ **Đây là quy tắc tự động để V2-2 chạy được, không phải chấm tay.**
> Plan yêu cầu holdout v2 phải có **≥ 15% ADVERSARIAL** và
> **≥ 30% NATURAL từ bạn cùng lớp** — hai con số này **chỉ đạt được ở V2-14**
> khi có dữ liệu Google Form (mục 6.3). Khi đó tier sẽ được gán lại thủ công.

## 4. Tag

| Tag | Điều kiện |
|---|---|
| `question` | câu chứa `?` |
| `short` | ≤ 5 từ |
| `long` | ≥ 10 từ |
| `no_diacritics` | không còn chữ nào ngoài ASCII (mất dấu) |
| `symbols` | không có chữ/số nào (emoji, `@@@@@@@`) |
| `typo` | khớp danh sách lỗi gõ |
| `compound` | ≥ 2 dấu phẩy (ứng viên câu ghép — chờ `Segmenter`) |
| `oos` | nhãn là `OUT_OF_SCOPE` |
| `security` | `oos` **và** chứa từ khóa bảo mật (mật khẩu, jwt, token, prompt, mã nguồn, database, reset, email, cấu hình) |
| `greeting_wrapper` | câu "chào + yêu cầu" đã bị sửa nhãn |

`tier` **không** lặp lại thành tag (tránh đếm 2 lần khi in breakdown).

## 5. Quy tắc gán `role`

Suy từ `roles` của intent trong `intents.json`:

- có `MEMBER` → `MEMBER`
- không có `MEMBER` nhưng có `BRANCH_MANAGER` → `STAFF`
- còn lại (chỉ `ADMIN`) → `ADMIN`

---

## 6. Trạng thái hiện tại (V2-2)

| Hạng mục | Số |
|---|---|
| Câu V1 đọc vào | 219 |
| Câu chuyển sang `holdout_v2.jsonl` | **215** |
| Câu tách sang `ambiguous.jsonl` | **4** (H004, H005, H015, H099 — kèm `reason`) |
| Câu sửa nhãn theo 6.4 | **1** (H001 → `LIST_PLANS`) |
| Phân bố tier | EASY **85** · NATURAL **125** · ADVERSARIAL **5** |
| Phân bố role | MEMBER **179** · STAFF **30** · ADMIN **6** |
| Số intent | 36 · thấp nhất **2 câu** (`GREETING`) |

**4 câu bị loại (và lý do):**

| Group | Câu | Lý do |
|---|---|---|
| H004 | `hello, có ai đó ở đây không` | chào + câu hỏi mơ hồ → không rõ `HELP` hay `GREETING` |
| H005 | `tôi vừa đăng ký hội viên xong` | câu trần thuật, không phải câu chào, không có yêu cầu |
| H015 | `cất điện thoại rồi` | thiếu tín hiệu để gán `GOODBYE` |
| H099 | `nên chọn gói nào` | cặp dễ nhầm `PLAN_COMPARE` / `PLAN_RECOMMEND` (6.5) |

> `GREETING` chỉ còn **2 câu** (`e ơi`, `chào bạn, tôi là thành viên mới`)
> sau khi tách — đây là hệ quả trực tiếp của việc làm nghiêm quy ước 6.4.
> **V2-14 phải bổ sung thêm** để chạm mục "≥ 15 câu/intent".

---

## 7. Cách tái tạo

```powershell
mvn -q -DskipTests compile
mvn -q exec:java "-Dexec.mainClass=com.gymfit.chat.training.HoldoutMigrator" `
     "-Dexec.classpathScope=compile"
```

Mọi thay đổi nhãn phải nằm trong bảng `RELABEL` / `AMBIGUOUS_REASONS`
của `HoldoutMigrator` (code = bằng chứng, không sửa tay file JSONL).

---

## 8. Việc còn phải làm (V2-14)

- [ ] Google Form 30–40 bạn cùng lớp → ≥ 400 câu làm **holdout** (không đưa vào train).
- [ ] Sprint viết tay 5 người × 12 intent mới × 10 câu = 600 câu cho **train**.
- [ ] Gán lại `tier` thủ công; đạt ≥ 30% `NATURAL` từ Form, ≥ 15% `ADVERSARIAL`.
- [ ] ≥ 900 câu, ≥ 15 câu/intent.
- [ ] Hai người gán độc lập ≥ 100 câu, **Cohen's κ ≥ 0,8**.
- [ ] Cặp tương phản ≥ 150 mẫu mỗi bên (mục 6.5).
- [ ] Ghi nguồn vào `docs/chatbot/DATA_SOURCES.md`.
