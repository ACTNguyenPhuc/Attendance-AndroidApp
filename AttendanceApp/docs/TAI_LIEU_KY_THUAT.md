# TÀI LIỆU KỸ THUẬT — Ứng dụng Điểm danh QR (AttendanceApp)

> Phiên bản: 1.0 · Cập nhật: 22/06/2026
> Tài liệu mô tả kiến trúc, công nghệ, cấu trúc thư mục dự án, thiết kế dữ liệu Firebase, luật bảo mật và các luồng hoạt động kỹ thuật chi tiết.

---

## 1. Tổng quan kỹ thuật

| Hạng mục | Chi tiết |
|----------|----------|
| Nền tảng | Android (native) |
| Ngôn ngữ | **Java 8** (không Kotlin, không Jetpack Compose) |
| UI | XML layouts + **View Binding** (`buildFeatures.viewBinding = true`) |
| Backend | **Firebase** (project `attendance-297e6`) |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| Kiến trúc | Plain Android, **Repository pattern** (singleton), không dùng DI framework |
| Bất đồng bộ | Firebase callback (`OnSuccessListener` / `OnFailureListener`) + `LiveData` cho real-time |

### Dịch vụ Firebase sử dụng
- **Firebase Authentication** — đăng nhập/đăng ký bằng email & mật khẩu.
- **Cloud Firestore** — cơ sở dữ liệu chính (NoSQL, real-time).
- **Firebase App Check** — Debug provider khi build debug; release cần provider thật (Play Integrity đã khai báo).
- **Firebase Cloud Messaging (FCM)** — khung nhận thông báo (chưa hoàn thiện nghiệp vụ).
- **Firebase Storage** — khai báo sẵn (chưa dùng nhiều).

### Thư viện bên thứ ba quan trọng (xem [app/build.gradle](../app/build.gradle))
- **ZXing** (`zxing-android-embedded`, `zxing core`) — sinh & quét mã QR.
- **Google Play Services Location** — lấy GPS (`FusedLocationProviderClient`).
- **ThreeTenABP** — backport Date/Time của Java 8.
- **MPAndroidChart** — biểu đồ thống kê.
- **MaterialCalendarView** — lịch.
- **Lottie** — animation màn hình kết quả.
- **Glide**, **CircleImageView** — ảnh/avatar.
- **CameraX**, **ML Kit Face Detection** — khai báo cho tính năng khuôn mặt (chưa kích hoạt).
- **Room** — khai báo cho cache cục bộ (chưa dùng nhiều).

---

## 2. Build & Run

> **Không có Gradle wrapper (`gradlew`)** trong repo. Dùng Android Studio (Hedgehog 2023.1+) hoặc Gradle cài sẵn cục bộ.

```bash
gradle assembleDebug          # build APK debug
gradle installDebug           # build + cài lên thiết bị/emulator
gradle test                   # unit test JVM (test/)
gradle connectedAndroidTest   # instrumented test trên thiết bị (androidTest/)
gradle clean
```

Chạy một lớp instrumented test cụ thể:
```bash
gradle connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.attendanceapplication.ExampleInstrumentedTest
```

**Lưu ý vận hành:**
- Cần **thiết bị thật** để test GPS + Camera (emulator chỉ hỗ trợ một phần).
- Bắt buộc có `app/google-services.json` để biên dịch (gắn project Firebase).
- `usesCleartextTraffic="true"` được bật trong manifest (phục vụ dev cục bộ).

---

## 3. Cấu trúc thư mục dự án

```
AttendanceApp/
├── app/
│   ├── build.gradle                 # cấu hình module app, dependencies
│   ├── google-services.json         # cấu hình Firebase (project attendance-297e6)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # khai báo permission, activity, service
│       │   ├── java/com/example/attendanceapplication/
│       │   │   ├── AttendanceApplication.java     # Application entry, init ThreeTen/Firebase/AppCheck
│       │   │   ├── activities/                    # Màn hình (Activity)
│       │   │   ├── fragments/                     # Fragment theo vai trò
│       │   │   │   ├── teacher/
│       │   │   │   ├── student/
│       │   │   │   └── shared/
│       │   │   ├── adapters/                      # RecyclerView adapters
│       │   │   ├── models/                        # POJO Firestore
│       │   │   ├── repositories/                  # FirebaseRepository (singleton)
│       │   │   └── utils/                         # Tiện ích dùng chung
│       │   └── res/
│       │       ├── layout/                        # XML layout (activity_*, fragment_*, item_*, dialog_*)
│       │       ├── menu/                          # menu toolbar
│       │       ├── drawable/, mipmap/, values/    # tài nguyên giao diện
│       │       └── assets/                        # Lottie json (success/error)
│       ├── test/                                  # unit test JVM
│       └── androidTest/                           # instrumented test
├── docs/                            # (Tài liệu này)
├── firestore.rules                  # NGUỒN SỰ THẬT về luật bảo mật Firestore
├── data.json                        # dữ liệu mẫu phản ánh cấu trúc collection
├── README.md                        # spec chức năng (tiếng Việt, chi tiết nhất)
└── CLAUDE.md                        # hướng dẫn cho công cụ AI làm việc với repo
```

### 3.1 Chi tiết package `activities/`

| File | Vai trò |
|------|---------|
| `SplashActivity` | Màn hình chờ; kiểm tra trạng thái đăng nhập & điều hướng theo `role`. |
| `LoginActivity` / `RegisterActivity` | Đăng nhập / đăng ký (Firebase Auth). |
| `TeacherMainActivity` | Khung chính của GV, host 4 fragment qua Bottom Navigation (Trang chủ, Lớp học, Lịch, Hồ sơ). |
| `StudentMainActivity` | Khung chính của SV, host 5 fragment qua Bottom Navigation (Trang chủ, Lớp học, Lịch, Lịch sử, Hồ sơ). |
| `CreateClassActivity` | Tạo lớp (DatePicker/TimePicker, chip ngày học, xem trước số buổi). |
| `ClassDetailTeacherActivity` | Chi tiết lớp (GV); dùng `ClassDetailPagerAdapter` (ViewPager2) cho 3 tab. |
| `ClassDetailPagerAdapter` | Adapter ViewPager2: tab 0 Buổi học, 1 Sinh viên, 2 Thống kê. |
| `SessionManagementActivity` | Mở/đóng phiên điểm danh, hiển thị QR, theo dõi real-time. |
| `CreateSessionActivity` | Khung tạo phiên thủ công (chỉ đặt tiêu đề; nghiệp vụ chính nằm ở SessionManagement). |
| `ShiftAttendanceListActivity` | Danh sách điểm danh của buổi (có mặt + vắng). |
| `ClassDetailStudentActivity` | Chi tiết lớp (SV); danh sách buổi + tỷ lệ chuyên cần. |
| `ShiftDetailActivity` | Chi tiết một buổi (SV); quyết định cho/không cho điểm danh. |
| `ScanAttendanceActivity` | Quét QR điểm danh + xác thực GPS (luồng 3 bước). |
| `JoinClassActivity` | Quét QR tham gia lớp. |
| `AttendanceResultActivity` | Màn kết quả điểm danh (Lottie, tự đóng sau 3s). |
| `ProfileActivity` | Hồ sơ (bản Activity, song song với ProfileFragment). |
| `SessionManagementActivityPlaceholder` | File placeholder/stale — **không dùng**, cần xác minh trước khi tham chiếu. |

### 3.2 Chi tiết package `fragments/`

**teacher/**
- `TeacherDashboardFragment` — Trang chủ GV: lời chào, số lớp, buổi học hôm nay, buổi đang chờ. Dùng `getUserProfile`, `getTeacherClasses`, `getClassStudents`, `getShiftsByDate`.
- `TeacherClassListFragment` — Danh sách lớp + tìm kiếm + xóa; ưu tiên hiển thị theo trạng thái buổi học (ongoing > upcoming > completed > cancelled). FAB tạo lớp, menu QR/Xóa.
- `TeacherCalendarFragment` — Lịch dạy + agenda; dùng `getShiftsByDate`, `getShiftsForClasses`. Buổi ongoing → SessionManagement; completed → ShiftAttendanceList.
- `ShiftsTabFragment` — Tab Buổi học trong chi tiết lớp; danh sách buổi real-time (`getClassShifts` qua `LiveData`); nút Mở điểm danh.
- `StudentsTabFragment` — Tab Sinh viên; thêm/xóa SV (`getClassStudents`, `getUnenrolledStudents`, `enrollStudent`, `removeEnrollment`, `deleteStudentAttendancesForClass`).
- `StatsTabFragment` — Tab Thống kê; biểu đồ tròn (phân loại SV) + cột (tỷ lệ theo buổi). Dùng `getClassShifts`, `getClassStudents`, `getClassAttendances`.

**student/**
- `StudentDashboardFragment` — Trang chủ SV: danh sách lớp, nút Tham gia/Quét. Dùng `getUserProfile`, `getStudentClasses`.
- `StudentClassListFragment` — Danh sách lớp đã tham gia + tìm kiếm; FAB tham gia lớp.
- `StudentCalendarFragment` — Lịch học các lớp đã tham gia; buổi → ShiftDetail/ScanAttendance.
- `AttendanceHistoryFragment` — Lịch sử điểm danh (`getStudentAttendanceHistory`).

**shared/**
- `ProfileFragment` — Hồ sơ dùng chung 2 vai trò; nút Đăng xuất (`signOut`) về Login.

### 3.3 Package `adapters/`
RecyclerView adapter, mỗi adapter cho một loại danh sách: `ShiftListAdapter` (buổi học GV), `RealtimeAttendanceAdapter` (điểm danh real-time), `ShiftAgendaAdapter`, `ShiftHomeAdapter`, `ClassCardAdapter`, `TeacherClassCardAdapter`, `AttendanceHistoryAdapter`.

### 3.4 Package `utils/`
- `AttendanceUtils` — hàm tĩnh thuần: Haversine `calculateDistance`, `isWithinRadius`, sinh QR bitmap (ZXing), `generateToken`, `generateSessionId`, `getDayOfWeekVN`, `daysFromToday`, `shouldShowUpcomingBadge`.
- `LocationService` — bọc `FusedLocationProviderClient` thành callback (one-shot + liên tục).
- `ClassQRDialog` — dialog hiển thị QR mời vào lớp (`{classId, type:"join"}`).
- `FCMService` — `FirebaseMessagingService` (khung, chưa lưu token).

---

## 4. Kiến trúc ứng dụng

### 4.1 Sơ đồ tầng

```
┌──────────────────────────────────────────────────────────────┐
│  TẦNG GIAO DIỆN (UI)                                          │
│  Activities + Fragments + Adapters (XML + View Binding)       │
└───────────────────────────┬──────────────────────────────────┘
                            │  gọi method, nhận callback / observe LiveData
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  TẦNG REPOSITORY (singleton)                                  │
│  FirebaseRepository.getInstance()                            │
│  - Toàn bộ Auth + Firestore CRUD tập trung tại đây           │
│  - Trả kết quả qua OnSuccess/OnFailure hoặc LiveData          │
└───────────────────────────┬──────────────────────────────────┘
                            │  FirebaseAuth / FirebaseFirestore
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  TẦNG BACKEND (Firebase)                                      │
│  Authentication · Cloud Firestore · App Check · FCM           │
└──────────────────────────────────────────────────────────────┘
```

**Nguyên tắc thiết kế cốt lõi:** UI **không bao giờ** gọi Firestore trực tiếp; mọi truy cập đều qua `FirebaseRepository`.

### 4.2 Quy ước trong `FirebaseRepository` (xem [FirebaseRepository.java](../app/src/main/java/com/example/attendanceapplication/repositories/FirebaseRepository.java))
- **Singleton** qua `getInstance()`, bọc `FirebaseAuth` + `FirebaseFirestore`.
- Tên collection là **hằng số private** (`COL_USERS`, `COL_CLASSES`, `COL_SHIFTS`, `COL_SESSIONS`, `COL_ATTENDANCES`, `COL_ENROLLMENTS`) — tái sử dụng, không hardcode chuỗi.
- Kết quả bất đồng bộ trả qua callback `OnSuccessListener` / `OnFailureListener` (không dùng coroutine/RxJava).
- **Đọc real-time trả về `LiveData`** (backed bởi `addSnapshotListener`): `getTeacherClasses`, `getStudentClasses`, `getClassShifts`.
- Lắng nghe real-time có hủy được trả về `ListenerRegistration` (ví dụ `listenToSessionAttendance`) — caller phải gỡ trong `onStop`/`onDestroy`.
- Ghi nhiều tài liệu dùng **WriteBatch** (giới hạn 500/batch, code chia theo lô 449/450 để an toàn).

### 4.3 Khởi tạo ứng dụng — [AttendanceApplication.java](../app/src/main/java/com/example/attendanceapplication/AttendanceApplication.java)
Đăng ký qua `android:name` trong manifest; trong `onCreate`:
1. `AndroidThreeTen.init(this)` — khởi tạo backport date/time.
2. `FirebaseApp.initializeApp(this)`.
3. Cài **Debug App Check provider** chỉ khi build debug; **release cần provider thật**.

---

## 5. Thiết kế dữ liệu Firebase (Cloud Firestore)

Cơ sở dữ liệu gồm **6 collection** ở cấp gốc. (Tham khảo `data.json` để xem dữ liệu mẫu và `README.md` cho danh sách trường đầy đủ.)

```
Firestore (root)
├── users/{uid}
├── classes/{classId}
├── shifts/{shiftId}            shiftId = classId + "_" + yyyy-MM-dd
├── enrollments/{studentId_classId}
├── sessions/{sessionId}        sessionId = "session_" + classId + "_" + shiftId + "_" + millis
└── attendances/{attendanceId}  auto-id
```

### 5.1 `users` — Người dùng
Tương ứng [models/User.java](../app/src/main/java/com/example/attendanceapplication/models/User.java). Document id = Firebase Auth UID.

| Trường | Kiểu | Mô tả |
|--------|------|------|
| `uid` | string | UID (trùng document id) |
| `studentCode` | string | Mã SV / mã GV |
| `name` | string | Họ tên |
| `email` | string | Email đăng nhập |
| `role` | string | `teacher` hoặc `student` |
| `avatarUrl` | string | URL ảnh đại diện (tùy chọn) |
| `createdAt` | timestamp | Thời điểm tạo |

### 5.2 `classes` — Lớp học
Tương ứng [models/ClassModel.java](../app/src/main/java/com/example/attendanceapplication/models/ClassModel.java). Document id = `classId` (do GV đặt).

| Trường | Kiểu | Mô tả |
|--------|------|------|
| `classId` | string | Mã lớp |
| `className` | string | Tên lớp |
| `teacherId` | string | UID giảng viên (chủ sở hữu) |
| `teacherName` | string | Tên giảng viên |
| `startDate` / `endDate` | string | Ngày bắt đầu/kết thúc (`yyyy-MM-dd`) |
| `schedule` | array\<int\> | Ngày học trong tuần, vd `[2,4]` = T2, T4 (CN = 8) |
| `startAt` / `endAt` | string | Giờ bắt đầu/kết thúc, vd `"18:00"` |
| `room` | string | Phòng học |
| `description` | string | Mô tả |
| `studentCount` | int | Số sinh viên (đệm hiển thị) |
| `createdAt` | timestamp | Thời điểm tạo |

### 5.3 `shifts` — Buổi học
Tương ứng [models/Shift.java](../app/src/main/java/com/example/attendanceapplication/models/Shift.java). Sinh tự động khi tạo lớp.

| Trường | Kiểu | Mô tả |
|--------|------|------|
| `shiftId` | string | `classId_yyyy-MM-dd` |
| `classId` / `className` | string | Thuộc lớp nào |
| `teacherName`, `title` | string | Hiển thị |
| `date` | string | Ngày buổi học (`yyyy-MM-dd`) |
| `dayOfWeek` | int | Thứ kiểu VN (2=T2 … 8=CN) |
| `startAt` / `endAt` | string | Giờ học |
| `room` | string | Phòng |
| `status` | string | `upcoming` / `ongoing` / `completed` / `cancelled` |
| `attendanceOpened` | boolean | Phiên điểm danh đang mở? |
| `attendanceSessionId` | string | ID phiên gắn với buổi (khi đang mở) |
| `createdAt` | timestamp | Thời điểm tạo |

> `attendanceStatus` trong model là **trạng thái cục bộ** (present/absent/not_checked), **không lưu** Firestore — chỉ dùng để render phía SV.

### 5.4 `enrollments` — Ghi danh
Tương ứng [models/Enrollment.java](../app/src/main/java/com/example/attendanceapplication/models/Enrollment.java). Document id = `studentId + "_" + classId` (đảm bảo duy nhất).

| Trường | Kiểu | Mô tả |
|--------|------|------|
| `studentId` | string | UID sinh viên |
| `classId` | string | Mã lớp |
| `joinedAt` | timestamp | Thời điểm tham gia |
| `status` | string | `active` / `inactive` |

### 5.5 `sessions` — Phiên điểm danh
Tương ứng [models/Session.java](../app/src/main/java/com/example/attendanceapplication/models/Session.java).

| Trường | Kiểu | Mô tả |
|--------|------|------|
| `sessionId` | string | ID phiên |
| `classId` / `shiftId` | string | Thuộc lớp/buổi |
| `latitude` / `longitude` | double | Tâm GPS (vị trí giảng viên) |
| `radius` | double | Bán kính hợp lệ (mét), mặc định 100 |
| `token` | string | Token QR hiện tại |
| `startTime` / `endTime` | timestamp | Mở/đóng phiên |
| `isActive` | boolean | Phiên còn hiệu lực? |
| `lateAfterMinutes` | int | Ngưỡng đi muộn (mặc định 15) |

### 5.6 `attendances` — Bản ghi điểm danh
Tương ứng [models/Attendance.java](../app/src/main/java/com/example/attendanceapplication/models/Attendance.java). Document id = auto-id.

| Trường | Kiểu | Mô tả |
|--------|------|------|
| `attendanceId` | string | ID bản ghi |
| `studentId` / `studentName` / `studentCode` | string | Sinh viên |
| `sessionId` / `shiftId` / `classId` | string | Bối cảnh điểm danh |
| `latitude` / `longitude` | double | Vị trí SV khi điểm danh |
| `distance` | double | Khoảng cách tới tâm (mét) |
| `checkinTime` | timestamp | Thời điểm điểm danh |
| `status` | string | `present` / `late` / `absent` |
| `selfieUrl` | string | Ảnh selfie (chưa dùng) |
| `faceVerified` | boolean | Đã xác thực khuôn mặt? (mặc định false) |
| `deviceId` | string | `ANDROID_ID` thiết bị (hỗ trợ chống gian lận) |

### 5.7 Quan hệ giữa các collection

```
users (teacher) ──1:N──► classes ──1:N──► shifts ──1:1──► sessions ──1:N──► attendances
   │                        ▲                                                   ▲
   │ (student)              │                                                   │
   └────────── enrollments ─┘ (N:N giữa users & classes)                       │
   └───────────────────────────────────────── 1:N ──────────────────────────────┘
```
- Một giảng viên sở hữu nhiều lớp.
- Một lớp có nhiều buổi học; mỗi buổi (khi mở) gắn 1 phiên.
- Quan hệ sinh viên ↔ lớp là **N:N** thông qua `enrollments`.
- Mỗi phiên sinh ra nhiều bản ghi điểm danh (mỗi SV 1 bản ghi).

---

## 6. Luật bảo mật Firestore

> `firestore.rules` là **nguồn sự thật** (block rules trong README chỉ mang tính minh họa và có thể lỗi thời).

Các bất biến chính được thực thi (xem [firestore.rules](../firestore.rules)):

| Collection | read | create | update | delete |
|-----------|------|--------|--------|--------|
| `users` | mọi user đã đăng nhập | — | chỉ chính chủ (`uid == request.auth.uid`) | chỉ chính chủ |
| `classes` | mọi user đã đăng nhập | người tạo phải là `teacherId` | chỉ `teacherId` của lớp | chỉ `teacherId` |
| `shifts` | mọi user đã đăng nhập | GV của lớp (kiểm tra qua `classId`) | GV của lớp & `classId` không đổi | GV của lớp |
| `sessions` | mọi user đã đăng nhập | mọi user đã đăng nhập | mọi user đã đăng nhập | mọi user đã đăng nhập |
| `attendances` | mọi user đã đăng nhập | SV tự tạo (`studentId == uid`) | mọi user đã đăng nhập (GV chỉnh sửa) | **cấm** (`false`) |
| `enrollments` | mọi user đã đăng nhập | SV tự tham gia **hoặc** GV của lớp thêm | chỉ GV của lớp | SV tự rời **hoặc** GV của lớp |

**Điểm cần lưu ý:**
- Bản ghi điểm danh **không thể bị xóa trực tiếp** qua luật (delete = false). Việc xóa khi xóa lớp/xóa SV được thực hiện ở tầng repository bằng quyền của giảng viên với chuỗi `deleteByQuery`.
- Quy tắc `sessions` đang khá lỏng (mọi user đăng nhập có thể create/update) — cần siết lại nếu nâng cấp bảo mật.
- Khi thay đổi cấu trúc dữ liệu, phải giữ nhất quán với các bất biến này.

---

## 7. Các luồng kỹ thuật chi tiết

### 7.1 Khởi động & điều hướng theo vai trò
```
SplashActivity.onCreate
  └─ postDelayed(2s) → checkAuthState()
        ├─ repo.getCurrentUser() == null  → LoginActivity
        └─ else repo.getUserProfile(uid)
              ├─ role == teacher → TeacherMainActivity
              └─ else            → StudentMainActivity
   (cờ FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK để xóa back stack)
```

### 7.2 Tạo lớp & tự động sinh buổi học
```
CreateClassActivity.createClass()
  → repo.getUserProfile(uid)            # lấy tên GV
      → repo.createClass(classModel)
            ├─ set classes/{classId}
            └─ generateShifts(classModel)        # private
                  loop ngày từ startDate..endDate
                    nếu getDayOfWeekVN(dow) ∈ schedule:
                       tạo Shift(status=upcoming, attendanceOpened=false)
                       batch.set(shifts/{classId_date})
                       (commit mỗi 499 doc, commit lần cuối)
```
- Quy đổi thứ: `Calendar.DAY_OF_WEEK` (1=CN…7=T7) → VN (2=T2…8=CN) bằng `AttendanceUtils.getDayOfWeekVN`.

### 7.3 Mở phiên điểm danh (giảng viên)
```
ShiftsTabFragment.openSession(shift) → SessionManagementActivity
  onCreate → createOrLoadSession()
    LocationService.getCurrentLocation()
      onLocationReady(loc) → buildAndCreateSession(lat,lng)
          tạo Session(sessionId, token, lat, lng, radius=100)
          repo.createSession(session)
            ├─ set sessions/{id} (startTime=now, isActive=true)
            └─ updateShiftStatus(shiftId, ONGOING, attendanceOpened=true, sessionId)
          displayQrCode(session)                # QR = {sessionId, token, classId}
          startRealtimeListener(id)             # listenToSessionAttendance
      onError → cảnh báo, dùng (0,0)
```

### 7.4 Theo dõi real-time & đóng phiên
```
repo.listenToSessionAttendance(sessionId)   # addSnapshotListener, orderBy checkinTime
  → onUpdate(list) → cập nhật RecyclerView + bộ đếm "x/y"

confirmClose() → closeSession()
  repo.closeSession(sessionId, shiftId)
    WriteBatch:
      update sessions/{id}: isActive=false, endTime=now
      update shifts/{shiftId}: status=completed, attendanceOpened=false
    onSuccess → attendanceListener.remove() → setResult(OK) → finish()
```
> Danh sách buổi (ShiftsTabFragment) tự cập nhật trạng thái mới qua `LiveData` real-time (`getClassShifts`) ngay khi buổi đổi `status`/`attendanceOpened`, không cần refresh thủ công.

### 7.5 Quét QR điểm danh (sinh viên) — luồng quan trọng nhất
```
ScanAttendanceActivity
  checkPermissions(CAMERA, ACCESS_FINE_LOCATION)
  startScanner() → barcodeView.decodeContinuous
    barcodeResult(text) [chống xử lý lặp bằng cờ isProcessing]
      processQrResult(text):
        parse JSON → sessionId, token
        repo.getSession(sessionId):
          ├─ !isActive            → lỗi "Mã QR đã hết hiệu lực"
          ├─ token != session.token → lỗi "Mã QR không hợp lệ"
          └─ repo.checkAlreadyAttended(studentId, sessionId):
               ├─ true  → lỗi "Đã điểm danh rồi"
               └─ false → getLocationAndVerify(session, studentId)
                    lấy GPS → distance = Haversine(SV, tâm phiên)
                    nếu distance > radius → lỗi "Không ở gần lớp (… m)"
                    else:
                      isLate = (now - startTime) > lateAfterMinutes(15)
                      Attendance(status = late ? LATE : PRESENT, distance, deviceId, …)
                      repo.saveAttendance(att)
                        → AttendanceResultActivity (success, distance, isLate)
                          (tự đóng sau 3s)
```

### 7.6 Tham gia lớp (sinh viên)
```
JoinClassActivity quét QR {classId, type:"join"}
  → repo.getClassById(classId) → BottomSheet xác nhận
    btnJoin → repo.checkEnrollment(studentId, classId)
       ├─ đã có → "Bạn đã là thành viên"
       └─ chưa  → repo.enrollStudent(studentId, classId)  # set enrollments/{studentId_classId}
```

### 7.7 Xóa lớp (xóa lan tỏa)
```
repo.deleteClass(classId)
  deleteByQuery(attendances where classId) →
   deleteByQuery(sessions where classId) →
    deleteByQuery(shifts where classId) →
     deleteByQuery(enrollments where classId) →
      delete classes/{classId}
  (mỗi deleteByQuery chia lô 450 doc/batch)
```

### 7.8 Thống kê chuyên cần (giảng viên)
```
StatsTabFragment.onResume → tải song song:
  getClassShifts(classId), getClassStudents(classId), getClassAttendances(classId)
  → tính tỷ lệ điểm danh theo buổi (bar chart)
  → phân loại SV: Xuất sắc >80% · Khá 50–80% · Yếu <50% (pie chart)
```

---

## 8. Bản đồ API của `FirebaseRepository`

| Nhóm | Method | Kiểu trả về |
|------|--------|-------------|
| Auth | `getCurrentUser`, `login`, `register`, `getUserProfile`, `signOut` | callback / FirebaseUser |
| Classes | `createClass`, `updateClassInfo`, `deleteClass`, `getClassById` | callback |
| Classes (real-time) | `getTeacherClasses`, `getStudentClasses` | `LiveData<List<ClassModel>>` |
| Shifts | `getShiftsByDate`, `getShiftsForClasses`, `updateShiftStatus` | callback / void |
| Shifts (real-time) | `getClassShifts` | `LiveData<List<Shift>>` |
| Enrollments | `enrollStudent`, `checkEnrollment`, `removeEnrollment`, `getClassStudents`, `getUnenrolledStudents`, `findStudentByCodeOrEmail`, `findStudentsByQuery` | callback |
| Sessions | `createSession`, `getSession`, `closeSession` | callback |
| Attendance | `saveAttendance`, `getClassAttendances`, `getShiftAttendances`, `getStudentAttendanceCount`, `getStudentAttendanceHistory`, `checkAlreadyAttended`, `deleteStudentAttendancesForClass` | callback |
| Attendance (real-time) | `listenToSessionAttendance` | `ListenerRegistration` |

> Đã loại bỏ `getClassShiftsOnce` (đọc một lần) — danh sách buổi nay dựa hoàn toàn vào `getClassShifts` real-time để tránh ghi đè dữ liệu cũ từ cache.

---

## 9. Quyền & cấu hình Manifest

**Permissions** (xem [AndroidManifest.xml](../app/src/main/AndroidManifest.xml)):
`INTERNET`, `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ/WRITE_EXTERNAL_STORAGE` (maxSdk 28), `FLASHLIGHT`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`.

**uses-feature** (bắt buộc): `camera`, `location.gps`.

**Cấu hình khác**: `android:name=".AttendanceApplication"`, `usesCleartextTraffic="true"`, theme tùy chỉnh; `FCMService` đăng ký nhận `MESSAGING_EVENT`.

---

## 10. Lưu ý kỹ thuật & nợ kỹ thuật (Technical Debt)

1. **App Check release**: build release cần cài provider thật (Play Integrity) — hiện chỉ có Debug provider khi debug.
2. **Vị trí dự phòng (0,0)**: khi GV không có GPS, phiên vẫn mở với tâm (0,0) → xác thực khoảng cách vô nghĩa. Nên chặn hoặc cảnh báo mạnh hơn.
3. **Khuôn mặt (ML Kit/CameraX)**: đã khai báo thư viện + trường dữ liệu nhưng **chưa có luồng** xác thực; `faceVerified` luôn `false`.
4. **FCM**: `onNewToken` chưa lưu token lên Firestore; chưa có nghiệp vụ gửi thông báo.
5. **Room**: khai báo nhưng chưa dùng để cache cục bộ.
6. **Luật `sessions`**: còn lỏng; nên siết quyền create/update theo chủ lớp.
7. **File stale**: `SessionManagementActivityPlaceholder.java` — xác nhận trước khi tham chiếu/sử dụng.
8. **Output build trong repo**: thư mục `app/build/` được commit — bỏ qua khi đọc hiểu mã nguồn.
9. **Index Firestore**: các truy vấn `whereEqualTo(...).orderBy(...)` và `whereIn` có thể cần composite index — đảm bảo đã tạo trên Firebase Console.

---

> Xem thêm **TAI_LIEU_NGHIEP_VU.md** để biết đặc tả use case và quy tắc nghiệp vụ.
