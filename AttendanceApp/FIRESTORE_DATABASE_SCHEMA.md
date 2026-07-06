# Cấu trúc Cloud Firestore

Ứng dụng sử dụng 6 collection cấp cao, không có subcollection. Mật khẩu người dùng được Firebase Authentication quản lý và không lưu trong Firestore.

## 1. `users/{uid}`

Lưu hồ sơ người dùng. Document ID là UID của Firebase Authentication.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `uid` | String | UID người dùng |
| `studentCode` | String | Mã sinh viên/giảng viên |
| `name` | String | Họ tên |
| `email` | String | Email đăng nhập |
| `role` | String | `student` hoặc `teacher` |
| `avatarUrl` | String/null | Đường dẫn ảnh đại diện |
| `createdAt` | Timestamp | Thời điểm tạo tài khoản |

## 2. `classes/{classId}`

Lưu thông tin lớp học. Document ID là mã lớp do giảng viên nhập.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `classId` | String | Mã lớp |
| `className` | String | Tên lớp |
| `teacherId` | String | UID giảng viên, tham chiếu `users` |
| `teacherName` | String | Tên giảng viên |
| `startDate`, `endDate` | String | Ngày bắt đầu/kết thúc, định dạng `yyyy-MM-dd` |
| `schedule` | List\<Number\> | Các thứ học: `2` đến `8`, trong đó `8` là Chủ nhật |
| `startAt`, `endAt` | String | Giờ học chung dự phòng, định dạng `HH:mm` |
| `daySchedules` | List\<Map\> | Lịch riêng từng thứ gồm `dayOfWeek`, `startAt`, `endAt` |
| `room` | String | Phòng học |
| `description` | String | Mô tả lớp |
| `studentCount` | Number | Số sinh viên |
| `createdAt` | Timestamp | Thời điểm tạo lớp |

## 3. `shifts/{shiftId}`

Lưu từng buổi học của lớp. Ca thường có ID `classId_yyyy-MM-dd`; ca học bù có thêm `_m{timestamp}`.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `shiftId` | String | Mã buổi học |
| `classId` | String | Tham chiếu `classes` |
| `className` | String | Tên lớp |
| `teacher` | String | Tên hiển thị của giảng viên |
| `teacherName` | String | Hiện đang lưu UID giảng viên |
| `title` | String | Tiêu đề buổi học |
| `date` | String | Ngày học, định dạng `yyyy-MM-dd` |
| `dayOfWeek` | Number | Thứ trong tuần, từ `2` đến `8` |
| `startAt`, `endAt` | String | Giờ bắt đầu/kết thúc, định dạng `HH:mm` |
| `room` | String | Phòng học |
| `status` | String | `upcoming`, `ongoing`, `completed` hoặc `cancelled` |
| `attendanceOpened` | Boolean | Điểm danh đang mở hay không |
| `makeup` | Boolean | Có phải ca học bù hay không |
| `attendanceSessionId` | String/null | Phiên điểm danh hiện tại |
| `content` | String/null | Nội dung buổi học |
| `createdAt` | Timestamp | Thời điểm tạo ca |

## 4. `sessions/{sessionId}`

Lưu phiên điểm danh của một buổi học. ID có dạng `session_{classId}_{shiftId}_{timestamp}`.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `sessionId` | String | Mã phiên điểm danh |
| `classId` | String | Tham chiếu `classes` |
| `shiftId` | String | Tham chiếu `shifts` |
| `latitude`, `longitude` | Number | Tọa độ điểm danh của giảng viên |
| `radius` | Number | Bán kính cho phép, đơn vị mét |
| `token` | String | Mã bí mật được nhúng trong QR |
| `startTime` | Timestamp | Thời điểm mở phiên |
| `scheduledEndTime` | Timestamp | Thời điểm kết thúc theo lịch học |
| `endTime` | Timestamp/null | Thời điểm đóng phiên thực tế |
| `active` | Boolean | Phiên còn hoạt động hay không |
| `content` | String/null | Nội dung buổi học |
| `lateAfterMinutes` | Number | Số phút sau khi mở phiên thì tính là muộn |

## 5. `attendances/{attendanceId}`

Lưu từng lượt điểm danh. Document ID là ID ngẫu nhiên do Firestore sinh.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `attendanceId` | String | Mã bản ghi điểm danh |
| `studentId` | String | UID sinh viên, tham chiếu `users` |
| `studentName` | String/null | Tên sinh viên |
| `studentCode` | String/null | Mã sinh viên |
| `sessionId` | String | Tham chiếu `sessions` |
| `shiftId` | String | Tham chiếu `shifts` |
| `classId` | String | Tham chiếu `classes` |
| `latitude`, `longitude` | Number | Tọa độ sinh viên khi điểm danh |
| `distance` | Number | Khoảng cách đến giảng viên, đơn vị mét |
| `checkinTime` | Timestamp | Thời điểm điểm danh |
| `status` | String | `present`, `late` hoặc `absent` |
| `selfieUrl` | String/null | Đường dẫn ảnh xác thực |
| `faceVerified` | Boolean | Kết quả xác thực khuôn mặt |
| `deviceId` | String | Mã thiết bị Android |

## 6. `enrollments/{studentId_classId}`

Lưu quan hệ sinh viên tham gia lớp.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `studentId` | String | UID sinh viên, tham chiếu `users` |
| `classId` | String | Tham chiếu `classes` |
| `joinedAt` | Timestamp | Thời điểm tham gia lớp |
| `status` | String | `active` hoặc `inactive` |

## Quan hệ chính

```text
users (teacher)  1 ── N classes
classes          1 ── N shifts
classes          1 ── N enrollments
shifts           1 ── N sessions
sessions         1 ── N attendances
users (student)  1 ── N enrollments/attendances
```
