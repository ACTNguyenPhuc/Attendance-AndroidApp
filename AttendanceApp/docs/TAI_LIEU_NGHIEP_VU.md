# TÀI LIỆU NGHIỆP VỤ — Ứng dụng Điểm danh QR (AttendanceApp)

> Phiên bản: 1.0 · Cập nhật: 22/06/2026
> Tài liệu mô tả nghiệp vụ, đối tượng sử dụng, các tình huống sử dụng (use case), quy tắc nghiệp vụ và luồng hoạt động chi tiết của ứng dụng.

---

## 1. Giới thiệu tổng quan

**AttendanceApp** là ứng dụng điểm danh dựa trên mã QR động dành cho môi trường giảng dạy (giảng viên – sinh viên), chạy trên nền tảng Android, sử dụng **Firebase** làm backend.

Mục tiêu chính: giúp giảng viên điểm danh nhanh, chính xác và **chống gian lận** thông qua 3 cơ chế kết hợp:

1. **Mã QR động (dynamic QR)** — mỗi phiên điểm danh sinh một token ngẫu nhiên, giảng viên có thể làm mới mã bất cứ lúc nào để ngăn việc chụp lại/chia sẻ ảnh QR.
2. **Xác thực vị trí GPS (Haversine)** — sinh viên chỉ điểm danh được khi đứng trong bán kính cho phép (mặc định **100m**) tính từ vị trí giảng viên.
3. **Đồng bộ thời gian thực (real-time Firestore)** — giảng viên nhìn thấy danh sách sinh viên điểm danh ngay lập tức khi sinh viên quét mã.

### Đặc điểm nghiệp vụ nổi bật
- Tạo lớp học **tự động sinh toàn bộ buổi học (shift)** theo lịch tuần và khoảng thời gian học kỳ.
- Phân quyền rõ ràng theo vai trò: **Giảng viên (teacher)** và **Sinh viên (student)**.
- Theo dõi tỷ lệ chuyên cần, thống kê theo lớp/sinh viên/buổi học.
- Phân biệt trạng thái điểm danh: **Có mặt (present)**, **Đi muộn (late)**, **Vắng (absent)**.

---

## 2. Đối tượng sử dụng (Actors)

| Actor | Mô tả | Quyền hạn chính |
|-------|-------|-----------------|
| **Giảng viên (Teacher)** | Người tạo và quản lý lớp học | Tạo/sửa/xóa lớp, quản lý sinh viên, mở/đóng phiên điểm danh, xem thống kê |
| **Sinh viên (Student)** | Người tham gia lớp và điểm danh | Tham gia lớp, quét QR điểm danh, xem lịch học, xem lịch sử điểm danh |
| **Hệ thống (System/Firebase)** | Backend xử lý dữ liệu | Xác thực, lưu trữ, đồng bộ real-time, áp dụng luật bảo mật |

> Vai trò được xác định khi đăng ký tài khoản và lưu trong trường `role` của collection `users`. Sau khi đăng nhập, hệ thống tự điều hướng đến màn hình tương ứng với vai trò.

---

## 3. Sơ đồ tổng thể chức năng

```
                          ┌─────────────────────────┐
                          │      ĐĂNG NHẬP / ĐĂNG KÝ │
                          └───────────┬─────────────┘
                                      │ (theo role)
              ┌───────────────────────┴────────────────────────┐
              ▼                                                  ▼
   ┌──────────────────────┐                        ┌──────────────────────┐
   │   GIẢNG VIÊN          │                        │   SINH VIÊN          │
   ├──────────────────────┤                        ├──────────────────────┤
   │ • Trang chủ          │                        │ • Trang chủ          │
   │ • Quản lý lớp học    │                        │ • Lớp học của tôi    │
   │ • Lịch dạy           │                        │ • Lịch học           │
   │ • Hồ sơ              │                        │ • Lịch sử điểm danh  │
   │                      │                        │ • Hồ sơ              │
   │ Chi tiết lớp:        │                        │                      │
   │  - Buổi học          │                        │ Tham gia lớp (QR)    │
   │  - Sinh viên         │                        │ Quét QR điểm danh    │
   │  - Thống kê          │                        │                      │
   │ Mở/đóng phiên điểm   │◄──── QR + GPS ────────►│ Điểm danh            │
   │ danh                 │      real-time         │                      │
   └──────────────────────┘                        └──────────────────────┘
```

---

## 4. Danh sách Use Case

### Nhóm UC chung
- **UC-01**: Đăng ký tài khoản
- **UC-02**: Đăng nhập
- **UC-03**: Đăng xuất
- **UC-04**: Xem hồ sơ cá nhân

### Nhóm UC Giảng viên
- **UC-10**: Tạo lớp học (tự động sinh buổi học)
- **UC-11**: Chỉnh sửa thông tin lớp học
- **UC-12**: Xóa lớp học
- **UC-13**: Hiển thị mã QR mời sinh viên vào lớp
- **UC-14**: Thêm sinh viên thủ công vào lớp
- **UC-15**: Xóa sinh viên khỏi lớp
- **UC-16**: Mở phiên điểm danh (tạo QR + GPS)
- **UC-17**: Làm mới mã QR trong phiên
- **UC-18**: Theo dõi điểm danh real-time
- **UC-19**: Đóng phiên điểm danh
- **UC-20**: Xem danh sách điểm danh của một buổi học (có mặt/vắng)
- **UC-21**: Xem thống kê chuyên cần của lớp
- **UC-22**: Xem lịch dạy

### Nhóm UC Sinh viên
- **UC-30**: Tham gia lớp học bằng quét QR
- **UC-31**: Quét QR để điểm danh (kèm xác thực GPS)
- **UC-32**: Xem lớp học đã tham gia & tỷ lệ chuyên cần
- **UC-33**: Xem chi tiết buổi học & điểm danh
- **UC-34**: Xem lịch học
- **UC-35**: Xem lịch sử điểm danh

---

## 5. Đặc tả Use Case chi tiết

### UC-01 — Đăng ký tài khoản
- **Actor**: Người dùng mới (sinh viên hoặc giảng viên).
- **Tiền điều kiện**: Có kết nối Internet.
- **Luồng chính**:
  1. Người dùng mở màn hình Đăng ký.
  2. Nhập: Họ tên, Mã số (mã SV/mã GV), Email, Mật khẩu, Xác nhận mật khẩu.
  3. Chọn vai trò: Sinh viên (mặc định) hoặc Giảng viên.
  4. Hệ thống kiểm tra hợp lệ: họ tên không rỗng, mã số không rỗng, email đúng định dạng, mật khẩu ≥ 6 ký tự, mật khẩu xác nhận khớp.
  5. Hệ thống tạo tài khoản trên Firebase Authentication và lưu hồ sơ vào collection `users`.
  6. Thông báo "Đăng ký thành công" và chuyển về màn hình Đăng nhập.
- **Luồng ngoại lệ**:
  - Email đã tồn tại / lỗi mạng → hiển thị thông báo lỗi, giữ nguyên màn hình.
- **Hậu điều kiện**: Tài khoản và hồ sơ người dùng được tạo.

### UC-02 — Đăng nhập
- **Actor**: Người dùng đã có tài khoản.
- **Luồng chính**:
  1. Nhập Email + Mật khẩu (nút Đăng nhập chỉ bật khi email không rỗng & mật khẩu ≥ 6 ký tự).
  2. Hệ thống xác thực qua Firebase Auth.
  3. Hệ thống tải hồ sơ người dùng, đọc `role`.
  4. Điều hướng: `teacher` → màn hình Giảng viên; ngược lại → màn hình Sinh viên.
- **Luồng ngoại lệ**:
  - Sai email/mật khẩu → "Email hoặc mật khẩu không đúng".
  - Không tải được hồ sơ → thông báo lỗi.
- **Ghi chú**: Màn hình Splash khi mở app sẽ tự kiểm tra trạng thái đăng nhập; nếu đã đăng nhập sẽ vào thẳng màn hình theo vai trò (sau ~2 giây).

### UC-03 — Đăng xuất
- **Actor**: Người dùng đã đăng nhập.
- **Luồng**: Vào Hồ sơ → nhấn Đăng xuất → hệ thống gọi `signOut()` → quay về màn Đăng nhập (xóa toàn bộ back stack).

### UC-10 — Tạo lớp học
- **Actor**: Giảng viên.
- **Tiền điều kiện**: Đã đăng nhập với vai trò giảng viên.
- **Luồng chính**:
  1. Nhập: Tên lớp, Mã lớp (do GV tự đặt), Mô tả (tùy chọn), Phòng (tùy chọn).
  2. Chọn Ngày bắt đầu, Ngày kết thúc (DatePicker).
  3. Chọn Giờ bắt đầu, Giờ kết thúc (TimePicker).
  4. Chọn các ngày học trong tuần (chip T2…CN).
  5. Hệ thống hiển thị **xem trước**: "Hệ thống sẽ tạo N buổi học (từ … → …, mỗi tuần …)".
  6. Nhấn Tạo lớp.
  7. Hệ thống lưu lớp vào `classes`, lấy tên giảng viên từ hồ sơ, rồi **tự động sinh toàn bộ buổi học (shifts)** cho từng ngày học khớp lịch trong khoảng thời gian.
  8. Thông báo thành công, trả về mã lớp vừa tạo.
- **Quy tắc**:
  - Các trường bắt buộc: Tên lớp, Mã lớp, Ngày bắt đầu/kết thúc, Giờ bắt đầu/kết thúc, ít nhất 1 ngày trong tuần.
  - Mỗi buổi học có `shiftId = classId + "_" + ngày (yyyy-MM-dd)`.
  - Tất cả buổi học mới có trạng thái mặc định **upcoming** (sắp diễn ra) và `attendanceOpened = false`.
- **Hậu điều kiện**: Lớp học + danh sách buổi học được tạo trong Firestore.

### UC-11 — Chỉnh sửa thông tin lớp học
- **Actor**: Giảng viên (chủ lớp).
- **Luồng**: Trong Chi tiết lớp → menu → Chỉnh sửa → sửa **Tên lớp / Phòng** → Lưu.
- **Quy tắc**: Tên lớp không được rỗng. Khi cập nhật lớp, hệ thống **cập nhật lan tỏa (cascade)** thông tin tương ứng cho các buổi học của lớp.

### UC-12 — Xóa lớp học
- **Actor**: Giảng viên (chủ lớp).
- **Luồng**: Từ danh sách lớp → menu lớp → Xóa.
- **Quy tắc nghiệp vụ quan trọng**: Khi xóa lớp, hệ thống xóa **toàn bộ dữ liệu liên quan** theo thứ tự: `attendances` → `sessions` → `shifts` → `enrollments` → cuối cùng là `classes`. Đây là thao tác không thể hoàn tác.

### UC-13 — Hiển thị mã QR mời vào lớp
- **Actor**: Giảng viên.
- **Luồng**: Chi tiết lớp → menu → QR. Hệ thống hiển thị mã QR chứa `{classId, type:"join"}` để sinh viên quét tham gia lớp.

### UC-14 — Thêm sinh viên thủ công
- **Actor**: Giảng viên.
- **Luồng**: Chi tiết lớp → tab Sinh viên → Thêm → tìm theo tên/mã SV/email → chọn → hệ thống ghi bản ghi `enrollment`.
- **Quy tắc**: Không thêm trùng sinh viên đã có trong lớp (kiểm tra `checkEnrollment`).

### UC-15 — Xóa sinh viên khỏi lớp
- **Actor**: Giảng viên.
- **Luồng**: Tab Sinh viên → chọn sinh viên → Xóa.
- **Quy tắc**: Khi xóa sinh viên khỏi lớp, hệ thống xóa bản ghi `enrollment` **và** các bản ghi điểm danh của sinh viên đó trong lớp (`deleteStudentAttendancesForClass`).

### UC-16 — Mở phiên điểm danh
- **Actor**: Giảng viên.
- **Tiền điều kiện**: Có quyền GPS; đứng tại vị trí lớp học.
- **Luồng chính**:
  1. Tại tab Buổi học → chọn buổi → "Mở điểm danh".
  2. Hệ thống lấy **vị trí GPS hiện tại** của giảng viên (làm tâm điểm danh).
  3. Hệ thống tạo `session` gồm: token QR ngẫu nhiên, tọa độ tâm (lat/lng), bán kính (100m), thời điểm bắt đầu, `isActive = true`.
  4. Cập nhật buổi học: `status = ongoing`, `attendanceOpened = true`, gắn `attendanceSessionId`.
  5. Hiển thị **mã QR** + thông tin vị trí + số sinh viên đã điểm danh.
- **Luồng ngoại lệ**:
  - Không lấy được GPS → cảnh báo và dùng vị trí mặc định (0,0) — khi đó việc xác thực khoảng cách sẽ không có ý nghĩa thực tế.
- **Hậu điều kiện**: Phiên điểm danh đang hoạt động, sinh viên có thể quét QR.

### UC-17 — Làm mới mã QR
- **Actor**: Giảng viên.
- **Luồng**: Trong phiên → nhấn "Làm mới mã QR" → token mới được sinh, QR vẽ lại.
- **Mục đích nghiệp vụ**: Chống gian lận do sinh viên chụp/chia sẻ ảnh QR cho người không có mặt.

### UC-18 — Theo dõi điểm danh real-time
- **Actor**: Giảng viên.
- **Luồng**: Khi phiên mở, màn hình tự cập nhật danh sách sinh viên vừa điểm danh và bộ đếm "Đã điểm danh: x/y sinh viên" ngay khi có người quét thành công (lắng nghe real-time).

### UC-19 — Đóng phiên điểm danh
- **Actor**: Giảng viên.
- **Luồng**:
  1. Nhấn "Đóng phiên" → xác nhận.
  2. Hệ thống cập nhật `session.isActive = false`, ghi `endTime`; đồng thời cập nhật buổi học `status = completed`, `attendanceOpened = false` (thực hiện bằng **batch** để đảm bảo nhất quán).
  3. Thoát màn hình; danh sách buổi học cập nhật trạng thái mới ngay lập tức (đồng bộ real-time).
- **Quy tắc**: Sau khi đóng, sinh viên không thể quét điểm danh phiên này nữa (QR hết hiệu lực vì `isActive = false`).

### UC-20 — Xem danh sách điểm danh của buổi học
- **Actor**: Giảng viên.
- **Luồng**: Với buổi học đã kết thúc → mở danh sách điểm danh. Màn hiển thị:
  - Tổng sinh viên, số đã điểm danh, số vắng.
  - Danh sách đã điểm danh (kèm trạng thái có mặt/đi muộn).
  - Danh sách vắng mặt (sinh viên trong lớp nhưng không có bản ghi điểm danh).

### UC-21 — Xem thống kê chuyên cần
- **Actor**: Giảng viên.
- **Luồng**: Chi tiết lớp → tab Thống kê. Hiển thị:
  - **Biểu đồ tròn**: phân loại sinh viên theo mức chuyên cần (xuất sắc > 80%, khá 50–80%, yếu < 50%).
  - **Biểu đồ cột**: tỷ lệ điểm danh theo từng buổi học.

### UC-22 — Xem lịch dạy
- **Actor**: Giảng viên.
- **Luồng**: Tab Lịch → xem lịch tháng có đánh dấu các buổi học; chọn ngày để xem danh sách buổi. Buổi đang diễn ra → mở phiên điểm danh; buổi đã kết thúc → xem danh sách điểm danh.

### UC-30 — Tham gia lớp học (quét QR)
- **Actor**: Sinh viên.
- **Luồng chính**:
  1. Mở "Tham gia lớp" → quét mã QR của lớp (do GV hiển thị).
  2. Hệ thống đọc `classId`, tải thông tin lớp, hiển thị xác nhận tên lớp.
  3. Sinh viên nhấn Tham gia.
  4. Nếu chưa là thành viên → tạo bản ghi `enrollment` (status `active`); nếu đã là thành viên → thông báo "Bạn đã là thành viên lớp này".
- **Hậu điều kiện**: Sinh viên xuất hiện trong danh sách lớp.

### UC-31 — Quét QR điểm danh (xác thực GPS)
- **Actor**: Sinh viên.
- **Tiền điều kiện**: Đã tham gia lớp; phiên điểm danh đang mở; có quyền Camera + GPS.
- **Luồng chính** (chia 3 bước hiển thị tiến trình):
  1. **Bước 1 — Đọc QR**: quét mã, lấy `sessionId` + `token`.
  2. Hệ thống kiểm tra phiên:
     - Phiên còn hoạt động (`isActive = true`)? Nếu không → "Mã QR đã hết hiệu lực".
     - Token khớp? Nếu không → "Mã QR không hợp lệ".
     - Đã điểm danh phiên này chưa? Nếu rồi → "Bạn đã điểm danh buổi học này rồi".
  3. **Bước 2 — Lấy GPS**: lấy vị trí hiện tại của sinh viên.
  4. **Bước 3 — Xác minh vị trí**: tính khoảng cách Haversine đến tâm phiên. Nếu vượt bán kính → "Bạn không ở gần lớp học (cách … m)".
  5. Xác định trạng thái: nếu quét trong vòng `lateAfterMinutes` (mặc định 15 phút) kể từ khi mở phiên → **present**, ngược lại → **late**.
  6. Lưu bản ghi `attendance` (kèm tọa độ, khoảng cách, deviceId).
  7. Chuyển sang màn hình kết quả (tự đóng sau 3 giây).
- **Luồng ngoại lệ**: QR sai định dạng, không lấy được GPS, lỗi lưu → hiển thị thông báo và cho phép quét lại.

### UC-32 — Xem lớp đã tham gia & chuyên cần
- **Actor**: Sinh viên.
- **Luồng**: "Lớp học của tôi" → xem danh sách lớp, tìm kiếm theo tên/mã. Mở chi tiết lớp → xem tỷ lệ điểm danh (số buổi có mặt / số buổi đã qua, %), thống kê có mặt/vắng và danh sách buổi học.

### UC-33 — Xem chi tiết buổi học & điểm danh
- **Actor**: Sinh viên.
- **Luồng**: Chi tiết lớp → chọn buổi học. Màn hiển thị một trong các trạng thái:
  - **Đã điểm danh** → vô hiệu nút điểm danh.
  - **Chưa điểm danh & phiên đang mở** → nút "Điểm danh ngay" mở màn quét QR.
  - **Chưa mở điểm danh** → thông báo chờ giảng viên.

### UC-34 — Xem lịch học
- **Actor**: Sinh viên.
- **Luồng**: Tab Lịch → xem các buổi học của các lớp đã tham gia trên lịch tháng; chọn ngày để xem buổi và đi tới điểm danh.

### UC-35 — Xem lịch sử điểm danh
- **Actor**: Sinh viên.
- **Luồng**: Tab Lịch sử → xem toàn bộ bản ghi điểm danh (tổng số, có mặt, vắng, tỷ lệ %).

---

## 6. Quy tắc nghiệp vụ (Business Rules)

| Mã | Quy tắc |
|----|---------|
| BR-01 | Quy ước thứ trong tuần kiểu Việt Nam: **2 = Thứ 2 … 7 = Thứ 7, 8 = Chủ nhật** (khác chuẩn `Calendar` của Java). |
| BR-02 | Tạo lớp tự động sinh buổi học cho **mọi ngày khớp lịch** trong khoảng [ngày bắt đầu, ngày kết thúc]. |
| BR-03 | Trạng thái buổi học: `upcoming` (sắp diễn ra) → `ongoing` (đang điểm danh) → `completed` (đã kết thúc); ngoài ra có `cancelled` (đã hủy). |
| BR-04 | Mở phiên điểm danh chuyển buổi sang `ongoing`; đóng phiên chuyển sang `completed`. |
| BR-05 | Bán kính điểm danh hợp lệ mặc định **100 mét**, tính bằng công thức Haversine. |
| BR-06 | Sinh viên điểm danh sau **15 phút** kể từ khi mở phiên bị đánh dấu **đi muộn (late)**. |
| BR-07 | Mỗi sinh viên chỉ điểm danh **một lần** cho mỗi phiên (`sessionId`). |
| BR-08 | QR chỉ hợp lệ khi phiên còn `isActive = true` **và** token khớp với token hiện tại của phiên. |
| BR-09 | Sinh viên là "vắng" của một buổi nếu buổi đã `completed` mà không có bản ghi điểm danh. |
| BR-10 | Tỷ lệ chuyên cần = (số buổi có mặt) / (số buổi đã qua) × 100; "buổi đã qua" = có mặt + vắng. |
| BR-11 | Bản ghi điểm danh **không được xóa** bởi người dùng (chỉ bị xóa khi xóa lớp hoặc xóa sinh viên khỏi lớp — do hệ thống thực hiện). |
| BR-12 | Chỉ **giảng viên chủ lớp** mới được sửa/xóa lớp và buổi học của lớp đó. |
| BR-13 | Phân loại chuyên cần sinh viên trong thống kê: Xuất sắc (>80%), Khá (50–80%), Yếu (<50%). |
| BR-14 | Nhãn "Sắp diễn ra" chỉ hiển thị khi buổi học diễn ra trong vòng 1 ngày (hôm nay hoặc ngày mai). |

---

## 7. Trạng thái & vòng đời

### 7.1 Vòng đời buổi học (Shift)
```
   [Tạo lớp]
       │
       ▼
  ┌──────────┐   GV mở phiên     ┌──────────┐   GV đóng phiên   ┌───────────┐
  │ upcoming │ ────────────────► │ ongoing  │ ────────────────► │ completed │
  └──────────┘                   └──────────┘                   └───────────┘
       │                                                              ▲
       └──────────────── (có thể bị hủy) ──────► cancelled            │
                                                                 (xem danh sách
                                                                  điểm danh)
```

### 7.2 Trạng thái điểm danh của sinh viên trong một buổi
- **present** — có mặt đúng giờ.
- **late** — có mặt nhưng muộn (> 15 phút sau khi mở phiên).
- **absent / not_checked** — không có bản ghi điểm danh (suy ra khi buổi đã `completed`).

---

## 8. Luồng nghiệp vụ end-to-end (kịch bản tiêu biểu)

**Kịch bản: Một buổi học hoàn chỉnh**

1. Giảng viên đăng nhập → vào lớp → tab Buổi học → chọn buổi hôm nay → **Mở điểm danh**.
2. App lấy GPS của giảng viên, tạo phiên + hiển thị QR. Buổi chuyển sang `ongoing`.
3. Sinh viên (đã ở trong lớp học vật lý) mở app → vào buổi học → **Điểm danh ngay** → quét QR.
4. App kiểm tra: phiên hợp lệ → token khớp → chưa điểm danh → khoảng cách GPS ≤ 100m → ghi nhận **present/late**.
5. Màn hình giảng viên hiển thị tên sinh viên vừa điểm danh **ngay lập tức**, bộ đếm tăng.
6. Hết giờ, giảng viên **Đóng phiên**. Buổi chuyển `completed`.
7. Giảng viên mở **Danh sách điểm danh** của buổi để xem ai có mặt / vắng.
8. Sinh viên xem lại trong **Lịch sử điểm danh**; tỷ lệ chuyên cần được cập nhật.

---

## 9. Phạm vi & giới hạn hiện tại

- **Chống gian lận GPS**: dựa vào GPS thiết bị sinh viên; nếu thiết bị giả lập vị trí (fake GPS) thì cần biện pháp bổ sung (chưa triển khai).
- **Nhận diện khuôn mặt**: model `Attendance` có sẵn trường `faceVerified`/`selfieUrl` và dự án đã khai báo thư viện ML Kit Face Detection, nhưng **luồng xác thực khuôn mặt chưa được kích hoạt** (mặc định `faceVerified = false`).
- **Thông báo đẩy (FCM)**: đã có service nhận thông báo nhưng **chưa lưu token và chưa gửi thông báo nghiệp vụ** (đang ở dạng khung).
- **Vị trí dự phòng (0,0)**: khi giảng viên không lấy được GPS, phiên vẫn mở với tâm (0,0) khiến việc xác thực khoảng cách không còn ý nghĩa — cần lưu ý khi vận hành thực tế.
- **Dữ liệu mẫu (mock)**: một số màn hình sinh viên hiển thị dữ liệu mẫu khi lớp chưa có buổi học/lịch sử thực, phục vụ minh họa.

---

> Xem thêm **TAI_LIEU_KY_THUAT.md** để biết chi tiết kiến trúc, cấu trúc thư mục, thiết kế dữ liệu Firebase và luật bảo mật.
