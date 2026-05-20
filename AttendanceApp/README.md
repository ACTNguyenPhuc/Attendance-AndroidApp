# QR Attendance App – Hướng dẫn cài đặt & sử dụng

## 📱 Tổng quan

Ứng dụng điểm danh thông minh cho sinh viên và giảng viên, chống gian lận bằng:
- **Mã QR động** (tự động làm mới)
- **GPS xác minh vị trí** (bán kính 100m)
- **Real-time Firestore** theo dõi điểm danh tức thì

---

## 🛠 Yêu cầu

| Thành phần | Version |
|---|---|
| Android Studio | Hedgehog 2023.1+ |
| Java JDK | 11+ |
| Android SDK | API 24 (Android 7.0) → API 34 |
| Firebase Project | `attendance-297e6` (đã cấu hình) |

---

## 🚀 Cách mở & build

1. **Clone / mở project** trong Android Studio
2. **Đồng bộ Gradle** – nhấn `Sync Now` khi được hỏi
3. **google-services.json** đã ở đúng vị trí: `app/google-services.json`
4. **Build & Run** trên thiết bị Android thật (khuyến nghị) hoặc emulator API 24+

> ⚠️ Cần thiết bị thật để test GPS và Camera

---

## 🗂 Cấu trúc thư mục

```
app/src/main/java/com/example/attendanceapplication/
├── activities/
│   ├── SplashActivity.java          # Màn hình khởi động
│   ├── LoginActivity.java           # Đăng nhập
│   ├── RegisterActivity.java        # Đăng ký (GV/SV)
│   ├── TeacherMainActivity.java     # Main GV (Bottom Nav)
│   ├── StudentMainActivity.java     # Main SV (Bottom Nav)
│   ├── CreateClassActivity.java     # Tạo lớp + tự sinh buổi học
│   ├── ClassDetailTeacherActivity.java  # Chi tiết lớp (GV): tabs
│   ├── ClassDetailStudentActivity.java  # Chi tiết lớp (SV): danh sách buổi
│   ├── SessionManagementActivity.java   # QR Code + real-time list (GV)
│   ├── ScanAttendanceActivity.java      # Quét QR + GPS (SV)
│   ├── JoinClassActivity.java           # Tham gia lớp bằng QR
│   ├── ShiftDetailActivity.java         # Chi tiết buổi học (SV)
│   └── AttendanceResultActivity.java    # Kết quả điểm danh
│
├── fragments/
│   ├── teacher/
│   │   ├── TeacherDashboardFragment.java
│   │   ├── TeacherClassListFragment.java
│   │   ├── TeacherCalendarFragment.java
│   │   ├── ShiftsTabFragment.java       # Tab buổi học
│   │   ├── StudentsTabFragment.java     # Tab sinh viên
│   │   └── StatsTabFragment.java        # Tab thống kê (PieChart)
│   ├── student/
│   │   ├── StudentDashboardFragment.java
│   │   ├── StudentClassListFragment.java
│   │   ├── StudentCalendarFragment.java # MaterialCalendarView
│   │   └── AttendanceHistoryFragment.java
│   └── shared/
│       └── ProfileFragment.java
│
├── models/
│   ├── User.java        # uid, name, email, role, studentCode
│   ├── ClassModel.java  # classId, teacherId, schedule, startDate/endDate
│   ├── Shift.java       # shiftId, date, dayOfWeek, attendanceOpened
│   ├── Session.java     # sessionId, token, lat/lng, radius, isActive
│   ├── Attendance.java  # studentId, sessionId, status, distance, selfieUrl
│   └── Enrollment.java  # studentId + classId
│
├── repositories/
│   └── FirebaseRepository.java   # Toàn bộ CRUD Firestore + Auth
│
├── adapters/
│   ├── ClassCardAdapter.java
│   ├── ShiftListAdapter.java          # GV – danh sách buổi dạy
│   ├── ShiftAgendaAdapter.java        # SV – agenda view
│   ├── RealtimeAttendanceAdapter.java # GV – real-time checkin list
│   ├── AttendanceHistoryAdapter.java  # SV – lịch sử
│   └── (StudentShiftAdapter inner)    # SV – buổi học trong lớp
│
└── utils/
    ├── AttendanceUtils.java  # Haversine, QR generate, token
    └── FCMService.java       # Firebase Cloud Messaging
```

---

## 🔥 Firestore Collections

```
users/         {uid, name, email, role, studentCode, avatarUrl}
classes/       {classId, className, teacherId, schedule, startDate, endDate, startAt, endAt, room}
shifts/        {shiftId, classId, date, dayOfWeek, startAt, endAt, status, attendanceOpened, attendanceSessionId}
enrollments/   {studentId_classId, studentId, classId, joinedAt, status}
sessions/      {sessionId, classId, shiftId, token, lat, lng, radius, startTime, isActive}
attendances/   {attendanceId, studentId, sessionId, shiftId, classId, lat, lng, distance, checkinTime, status}
```

---

## 🔄 Luồng hoạt động chính

### Giảng viên
1. Đăng nhập → **TeacherMainActivity**
2. Tạo lớp → **CreateClassActivity** (hệ thống tự tạo tất cả buổi học)
3. Chi tiết lớp → **ClassDetailTeacherActivity** (3 tabs: Buổi học / Sinh viên / Thống kê)
4. Mở điểm danh → **SessionManagementActivity** (QR Code + real-time list)
5. Sinh viên scan → Firestore cập nhật tức thì trên màn hình GV

### Sinh viên  
1. Đăng nhập → **StudentMainActivity**
2. Tham gia lớp → **JoinClassActivity** (scan QR của GV)
3. Điểm danh → **ScanAttendanceActivity** (quét QR + GPS verify)
4. Xem lịch sử → **AttendanceHistoryFragment** (tỉ lệ, thống kê)
5. Xem lịch học → **StudentCalendarFragment** (MaterialCalendarView)

---

## ⚙️ Cấu hình Firebase

Firebase project `attendance-297e6` đã được cấu hình. Cần bật:

1. **Authentication** → Email/Password
2. **Firestore Database** → Start in production mode
3. **Storage** (tùy chọn, cho ảnh selfie)
4. **Cloud Messaging** (tùy chọn, thông báo)

### Firestore Security Rules (recommend)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth.uid == userId;
    }
    match /classes/{classId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'teacher';
    }
    match /shifts/{shiftId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'teacher';
    }
    match /sessions/{sessionId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'teacher';
    }
    match /attendances/{attId} {
      allow read: if request.auth != null;
      allow create: if request.auth.uid == request.resource.data.studentId;
    }
    match /enrollments/{docId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

---

## 📋 Tính năng đã hoàn thành

- [x] Đăng ký / Đăng nhập (GV + SV)
- [x] Tạo lớp học + tự sinh buổi học theo lịch
- [x] Tham gia lớp qua QR Code
- [x] Mở phiên điểm danh với QR động
- [x] Điểm danh: quét QR → xác minh GPS (Haversine ≤ 100m)
- [x] Real-time checkin list trên màn hình GV
- [x] Lịch sử điểm danh + tỉ lệ phần trăm
- [x] Thống kê lớp (PieChart)
- [x] Màn hình lịch học (MaterialCalendarView)
- [x] Profile GV/SV + đăng xuất
- [x] Bottom Navigation riêng cho GV và SV

## 🔧 Tính năng có thể mở rộng
- [ ] Xác minh khuôn mặt (ML Kit Face Detection đã import)
- [ ] Push notification khi GV mở điểm danh
- [ ] Export báo cáo Excel/PDF
- [ ] Nhận diện thiết bị (chống share QR)
- [ ] Ảnh selfie khi điểm danh

---

*Build by QR Attendance App – Firebase + Android Java*
