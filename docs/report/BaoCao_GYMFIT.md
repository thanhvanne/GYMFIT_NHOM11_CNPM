TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HCM
KHOA CÔNG NGHỆ THÔNG TIN
༺ ❀ ༻
MÔN HỌC: CÔNG NGHỆ PHẦN MỀM
BÁO CÁO CUỐI KỲ
HỆ THỐNG QUẢN LÝ PHÒNG TẬP GYM
  
                                                                                   GVHD: Hoàng Công Trình
                                                                                   NHÓM THỰC HIỆN: 11
                                                                             Mã lớp học: SOEN330679_09

                          1. Thạch Kim Đồng - 24110203
                          2. Nguyễn Văn Trường Sa - 24110317
                          3. Phạm Nhật Tân - 24110323
                          4. Trần Thu Uyên - 24110378
                          5. Nguyễn Thanh Vân - 24110379
Thành phố Hồ Chí Minh, tháng 10 năm 2026

MỤC LỤC
DANH SÁCH HÌNH ẢNH

LỜI MỞ ĐẦU
Trong những năm gần đây, nhu cầu tập luyện thể dục thể thao ngày càng tăng, kéo theo sự phát triển của các phòng tập gym với quy mô và hình thức hoạt động đa dạng. Điều này đặt ra yêu cầu về việc tổ chức và quản lý hoạt động phòng tập một cách khoa học, từ quản lý hội viên, lịch đặt chỗ đến các gói dịch vụ và doanh thu. Nếu các công việc này chủ yếu được thực hiện thủ công, quá trình quản lý có thể mất nhiều thời gian, khó kiểm soát thông tin và dễ xảy ra sai sót.

Xuất phát từ nhu cầu thực tế đó, nhóm lựa chọn đề tài “Xây dựng hệ thống quản lý phòng tập gym” nhằm vận dụng kiến thức về phân tích, thiết kế và phát triển phần mềm để xây dựng một hệ thống hỗ trợ quản lý hoạt động phòng tập. Hệ thống hướng đến việc tập trung hóa dữ liệu, đơn giản hóa các nghiệp vụ quản lý và nâng cao hiệu quả xử lý thông tin. Bên cạnh các chức năng quản lý cơ bản, đề tài còn nghiên cứu khả năng ứng dụng trí tuệ nhân tạo thông qua chatbot local (tự huấn luyện) để tư vấn khách hàng và hỗ trợ đặt lịch tự động, góp phần nâng cao tính tiện lợi trong quá trình sử dụng.

Thông qua quá trình thực hiện đề tài, nhóm có cơ hội củng cố kiến thức chuyên ngành, rèn luyện kỹ năng làm việc nhóm và vận dụng quy trình phát triển phần mềm vào một bài toán thực tế. Mặc dù đã có những nỗ lực trong quá trình nghiên cứu và xây dựng, hệ thống vẫn có thể tồn tại những hạn chế nhất định. Nhóm mong nhận được sự góp ý của quý thầy/cô để tiếp tục hoàn thiện đề tài.

PHẦN 1: GIỚI THIỆU ĐỀ TÀI

1.1. Lý do chọn đề tài
Ngành công nghiệp fitness tại Việt Nam đang phát triển nhanh chóng trong những năm gần đây. Tuy nhiên, hầu hết các phòng tập gym hiện tại vẫn sử dụng các công cụ quản lý khá cơ bản. Một số phòng gym chỉ sử dụng Excel để lưu trữ thông tin thành viên, theo dõi lịch đặt chỗ, và quản lý thanh toán. Những phòng gym có quy mô lớn hơn có thể sử dụng một số phần mềm quản lý đơn giản, nhưng các phần mềm này cũng chỉ cung cấp những tính năng cơ bản và không được tối ưu hóa riêng cho ngành fitness.

Việc sử dụng Excel hoặc những phần mềm không tối ưu này tạo ra nhiều khó khăn. Thứ nhất, dữ liệu thành viên nằm rải rác trong nhiều file, dễ bị trùng lặp hoặc không cập nhật kịp thời. Thứ hai, việc tư vấn cho khách hàng mới chỉ dựa vào kinh nghiệm của nhân viên, không có công cụ hỗ trợ. Thứ ba, đặt lịch tập luyện (booking) và check-in vẫn thủ công, dễ gây quá tải cho bộ phận lễ tân. Cuối cùng, lập báo cáo và theo dõi doanh thu rất mất thời gian vì phải tổng hợp dữ liệu từ nhiều file.

Vì vậy, việc xây dựng một hệ thống quản lý phòng tập gym chuyên dụng là cần thiết. Hệ thống sẽ tập trung vào những nhu cầu chính: quản lý thông tin thành viên một cách tập trung và hiệu quả, cung cấp công cụ tự động để hỗ trợ khách hàng đăng ký các dịch vụ, và tạo báo cáo tự động để quản lý dễ dàng theo dõi doanh thu cũng như những chỉ số quan trọng khác. Đặc biệt, hệ thống sẽ tích hợp chatbot AI nội bộ (local) để trả lời các câu hỏi thường gặp và đặt lịch tự động, giúp giảm tải đáng kể cho nhân viên.

1.2. Mục tiêu nghiên cứu
Mục tiêu chính của đề tài là xây dựng một hệ thống quản lý phòng tập gym để tự động hóa các quy trình quản lý, nâng cao chất lượng dịch vụ khách hàng, và hỗ trợ ra quyết định kinh doanh.

Phạm vi và giới hạn: 
- Quản lý thông tin thành viên, quản lý gói tập, quản lý đặt lịch (Booking).
- Quản lý check-in bằng mã QR (thời hạn 60 giây).
- Quản lý kho sản phẩm và bán hàng (POS).
- Tích hợp Chatbot AI Local tự huấn luyện để tư vấn và đặt lịch.
- Hệ thống KHÔNG bao gồm chức năng quản lý huấn luyện viên cá nhân (PT), lớp học tập thể (GroupX), hay tối ưu hóa lịch học tự động.

1.3. Điểm mới của đề tài
Đề tài này có những điểm mới so với các hệ thống quản lý phòng gym hiện tại trên thị trường:
- Tích hợp Chatbot AI Local: Hệ thống sử dụng mô hình tự huấn luyện bằng thuật toán Softmax Regression thuần Java để tư vấn khách hàng 24/7 và thực hiện đặt lịch tự động (thay vì gọi API OpenAI hay Gemini). Điều này giúp hệ thống hoạt động khép kín, đảm bảo an toàn tuyệt đối cho dữ liệu khách hàng.
- Check-in bảo mật với QR Code Động: Việc sử dụng mã QR thời gian thực (hết hạn sau 60 giây) và chống dùng lại (Replay Attack) bằng cơ chế lưu vết JTI giúp quá trình kiểm soát phòng tập nhanh chóng và bảo mật hơn, tránh chia sẻ thẻ tập.
- Cơ chế quản lý đa chi nhánh phân quyền rõ ràng: Dashboard thống kê tự động thay đổi số liệu theo vai trò (Member, Branch Manager, Admin).

1.4. Công nghệ sử dụng
- Ngôn ngữ lập trình: Java 17, JavaScript, HTML, CSS.
- Backend Framework: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security).
- Frontend: Thymeleaf (Server-side rendering), Vanilla JS.
- Cơ sở dữ liệu: Microsoft SQL Server (với Hibernate ddl-auto: validate).
- Bảo mật & Phân quyền: JSON Web Token (JWT) stateless, Role-Based Access Control (RBAC).
- Tính năng mở rộng: ZXing (Tạo QR Code), OpenPDF (Xuất PDF), Mô hình AI Softmax Regression thuần Java (NLP Intent Classification).

PHẦN 2: PHÂN TÍCH THIẾT KẾ HỆ THỐNG

2.1. Lược đồ Use Case tổng quát
Hệ thống gồm 3 tác nhân (Actor) chính với các nhóm quyền riêng biệt:
- Member (Hội viên): Đăng ký/Đăng nhập, Tra cứu gói tập, Xem chi tiết membership, Đặt lịch tập (Booking), Hủy lịch tập, Lấy mã QR Check-in, Chat với AI.
- Branch Manager (Quản lý chi nhánh): Xem Dashboard thống kê (doanh thu, lượt check-in), Quản lý đặt lịch tại chi nhánh, Quản lý tồn kho sản phẩm, Xem báo cáo bán hàng.
- Admin (Quản trị viên): Quản lý chi nhánh, Xem nhật ký hệ thống (Audit Logs), Gán nhãn dữ liệu câu hỏi cho Chatbot (Chatbot Admin) để huấn luyện lại mô hình.

2.2. Đặc tả Use Case
2.2.1. Quản lý thông tin & Gói tập (Member)
- Tên Use Case: Xem và Mua gói tập
- Mô tả: Hội viên đăng nhập, vào danh mục gói tập để xem các gói hiện có của chi nhánh. 
- Luồng sự kiện chính:
  1. Member chọn chi nhánh.
  2. Hệ thống hiển thị các gói tập (membership_plan) kèm giá trị và thời hạn.
  3. Member chọn gói, hệ thống chuyển sang màn hình thanh toán.
  4. Sau khi thanh toán thành công, hệ thống ghi nhận sales_order và kích hoạt membership.
- Ngoại lệ: Nếu Member đã có gói đang Active, hệ thống báo lỗi ConflictException.

2.2.2. Đặt lịch tập - Booking (Member)
- Tên Use Case: Đặt lịch sử dụng dịch vụ
- Mô tả: Đăng ký một khung giờ sử dụng phòng tập theo dịch vụ trong gói.
- Luồng sự kiện chính:
  1. Member chọn ngày và thời gian mong muốn.
  2. Hệ thống kiểm tra sức chứa của cơ sở (facility capacity) và giờ mở cửa.
  3. Nếu thỏa mãn, tạo record booking với trạng thái CONFIRMED.
- Ngoại lệ: Nếu sức chứa đã đầy, hệ thống báo lỗi facility_full. Nếu Member đặt sai dịch vụ (không có trong gói), báo lỗi membership_service_not_allowed.

2.2.3. Check-in (Member & Lễ tân)
- Tên Use Case: Check-in tự động qua QR
- Luồng sự kiện chính:
  1. Member nhấn nút "Tạo QR" trên giao diện.
  2. Hệ thống sinh mã QR chứa thông tin JWT đã mã hóa, thời hạn 60s.
  3. Lễ tân/Máy quét đọc mã. Hệ thống giải mã, kiểm tra thẻ hợp lệ, kiểm tra giờ mở cửa.
  4. Nếu hợp lệ, ghi nhận vào bảng check_in trạng thái ACCEPTED.
- Ngoại lệ: Nếu mã hết hạn hoặc đã được sử dụng (Replay Attack), hệ thống báo MEMBERSHIP_INVALID và ghi nhận REJECTED.

2.2.4. Trợ lý ảo Chatbot (Member)
- Tên Use Case: Hỏi đáp và Đặt lịch qua Chatbot
- Luồng sự kiện chính:
  1. Khách hàng gửi tin nhắn (VD: "Đặt lịch tập gym 19h tối nay").
  2. Chatbot phân tích Text, trích xuất Entity (Dịch vụ: Gym, Giờ: 19:00, Ngày: Hôm nay).
  3. Chatbot gọi API tìm kiếm Slot và hiển thị nút "Xác nhận".
  4. Khách hàng bấm Xác nhận. Hệ thống tự động gọi hàm đặt lịch (BookingService.create).

2.2.5. Thống kê Báo cáo (Branch Manager)
- Tên Use Case: Xem Dashboard và Báo cáo
- Mô tả: Branch Manager xem số liệu hoạt động của chi nhánh mình quản lý.
- Luồng sự kiện chính:
  1. Manager đăng nhập và chọn mục Báo cáo.
  2. Hệ thống gọi ReportService.revenue và ReportService.dashboard để lấy số liệu tổng doanh thu, lượt khách, đơn hàng.
  3. Hiển thị dưới dạng biểu đồ và bảng dữ liệu.

2.3. Biểu đồ tuần tự (Sequence Diagram)
- Kịch bản Đặt lịch (Booking):
  1. Member -> UI: Chọn lịch tập.
  2. UI -> BookingController: POST /api/v1/bookings (Kèm JWT Token).
  3. BookingController -> SecurityContext: Trích xuất AppPrincipal.
  4. BookingController -> BookingService: create(request).
  5. BookingService -> MembershipRepository: Kiểm tra gói hiện tại có hợp lệ không.
  6. BookingService -> FacilityRepository: Kiểm tra sức chứa hiện tại của phòng.
  7. BookingService -> BookingRepository: save(booking).
  8. BookingService trả về BookingResponse cho Controller và UI hiển thị thành công.

- Kịch bản Trò chuyện với Chatbot (Chatbot NLP Flow):
  1. User gửi Text.
  2. ChatController -> ChatService: Gọi phương thức xử lý tin nhắn.
  3. ChatService -> TextNormalizer: Loại bỏ dấu, sửa teencode.
  4. ChatService -> EntityExtractor: Nhận diện ngày, giờ, chi nhánh.
  5. ChatService -> IntentClassifier: Chạy Softmax Regression dự đoán ý định (VD: BOOKING_CREATE).
  6. ChatService -> DialogueManager: Điều phối trạng thái. Nếu đã đủ thông tin, hiển thị nút Xác nhận cho User.

PHẦN 3: THIẾT KẾ CƠ SỞ DỮ LIỆU

3.1. Entity Relationship Diagram (ERD)
Cơ sở dữ liệu được thiết kế theo chuẩn dạng chuẩn 3 (3NF), gồm 20 bảng chia thành các phân hệ:
- Phân hệ tổ chức: branch, branch_operating_hour, facility, branch_service_config.
- Phân hệ người dùng: app_user, member.
- Phân hệ dịch vụ: membership_plan, plan_service.
- Phân hệ bán hàng: product, stock_level, stock_movement, sales_order, sales_order_item, payment.
- Phân hệ nghiệp vụ tập luyện: membership, membership_service_access, booking, check_in, qr_token_use.
- Phân hệ quản trị & AI: audit_event, chat_session, chat_message, chat_training_candidate.

3.2. Từ điển dữ liệu (Các bảng chính)
- Bảng app_user (Tài khoản người dùng)
  + id (BIGINT, PK): Khóa chính.
  + email (VARCHAR): Email đăng nhập, Unique.
  + password_hash (VARCHAR): Mật khẩu đã băm.
  + role_code (VARCHAR): Quyền (ADMIN, BRANCH_MANAGER, MEMBER).

- Bảng member (Thông tin hội viên)
  + id (BIGINT, PK): Khóa chính.
  + member_code (VARCHAR): Mã hội viên định danh.
  + full_name (NVARCHAR): Họ tên đầy đủ.

- Bảng booking (Đặt lịch tập)
  + id (BIGINT, PK): Khóa chính.
  + booking_code (VARCHAR): Mã booking.
  + member_id (BIGINT, FK): Liên kết đến người đặt.
  + facility_id (BIGINT, FK): Liên kết cơ sở tập.
  + starts_at_utc (DATETIME): Thời gian bắt đầu.
  + status (VARCHAR): Trạng thái (CONFIRMED, CANCELLED, COMPLETED).

- Bảng check_in (Lịch sử Check-in)
  + id (BIGINT, PK): Khóa chính.
  + member_id (BIGINT, FK): Khách hàng.
  + result (VARCHAR): ACCEPTED hoặc REJECTED.
  + reason (NVARCHAR): Lý do nếu bị từ chối (Vd: Sai chi nhánh, Hết hạn).

- Bảng chat_training_candidate (Dữ liệu học của Chatbot)
  + text (NVARCHAR): Nội dung câu hỏi chưa hiểu.
  + predicted_intent (VARCHAR): Intent máy dự đoán.
  + label (VARCHAR): Nhãn do Admin gán lại để train tiếp.

PHẦN 4: GIAO DIỆN HỆ THỐNG
Hệ thống sử dụng Server-side rendering với Thymeleaf kết hợp với CSS/JS.
- Giao diện Admin: Liệt kê chi nhánh dạng bảng (DataTables). Cung cấp màn hình "Chatbot Training" nơi hiển thị các câu hỏi người dùng bị Fallback (confidence thấp) để Admin gán lại nhãn (Labeling) và tái huấn luyện mô hình.
- Giao diện Branch Manager: Dashboard thống kê rõ ràng với các KPI chính: Tổng doanh thu (VNĐ), Số gói bán ra, Tổng lượt Check-in, Bảng cảnh báo kho tồn thấp.
- Giao diện Member: Hiển thị nổi bật thẻ Membership (mã QR động). Phía dưới là danh sách các lịch đặt chỗ sắp tới, có nút Hủy lịch.
- Giao diện Chatbot: Một widget luôn nằm ở góc phải màn hình. Cung cấp các nút "Gợi ý nhanh" (Quick Replies) hoặc các Card thông tin (Ví dụ: Card xác nhận đặt lịch với nút Đồng ý/Từ chối) thông qua HTML động (DOM manipulation).

KẾT LUẬN
Đề tài đã hoàn thành việc xây dựng một hệ thống quản lý phòng tập gym toàn diện với các chức năng chi tiết bao gồm quản lý thành viên, đặt chỗ, sản phẩm, doanh thu và check-in QR bảo mật. Hệ thống tập trung hóa dữ liệu vào một nền tảng duy nhất, khắc phục triệt để nhược điểm của việc quản lý rời rạc qua Excel. 

Điểm nổi bật nhất của đề tài là việc hiện thực hóa và ứng dụng một hệ thống Chatbot AI Local tự huấn luyện chuyên biệt (viết bằng Java thuần) thay vì sử dụng API của bên thứ 3. Mô hình Softmax Regression kết hợp với hệ thống điều phối Dialogue Manager đã giúp tự động hóa quy trình tư vấn và đặt lịch thông qua hội thoại, mang lại tính tương tác cao và tiện lợi cho khách hàng.

Mặc dù vậy, hệ thống hiện tại vẫn còn một số điểm có thể cải thiện. Mô hình Chatbot Local xử lý tốt các luồng hội thoại thiết lập sẵn và câu hỏi ngắn, nhưng chưa linh hoạt trước các đoạn hội thoại quá phức tạp hoặc sai chính tả nặng. Ngoài ra, module thanh toán hiện đang ở mức giả lập quy trình. Trong tương lai, hệ thống có thể tích hợp cổng thanh toán MoMo/VNPay thực tế, đồng thời nâng cấp mô hình AI bằng thuật toán học sâu (Deep Learning) để cải thiện độ nhận diện ngôn ngữ tự nhiên, cũng như phát triển ứng dụng di động riêng để tối ưu hóa trải nghiệm khách hàng.

TÀI LIỆU THAM KHẢO
[1] Docs.spring.io. (2023). "Spring Boot Reference Documentation".
[2] Hibernate.org. (2023). "Hibernate ORM User Guide".
[3] Jurafsky, D., & Martin, J. H. (2023). Speech and Language Processing (3rd ed.) - Chương Logistic Regression (Softmax).
[4] Thymeleaf.org. (2023). "Thymeleaf - Server-side Java template engine".
