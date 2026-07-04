# Thông báo cục bộ nhắc lịch học (Local Notification)

Tài liệu mô tả cơ chế thông báo nhắc trước ca học/ca dạy trong ứng dụng điểm danh.
Hệ thống **không dùng Firebase Cloud Messaging (FCM)** — mọi thông báo được tạo, lên
lịch và hiển thị **ngay trên thiết bị**.

---

## 1. Tổng quan

Ứng dụng tự nhắc **sinh viên** (trước ca học) và **giảng viên** (trước ca dạy) một
khoảng thời gian có thể cấu hình (mặc định **15 phút**). Khi nhấn vào thông báo, app mở
thẳng màn hình liên quan đến ca đó.

Cơ chế cốt lõi: **app không tự canh giờ**. Nó nhờ hệ điều hành (AlarmManager) giữ giúp
các "báo thức", còn app chỉ lưu vài thông tin nhỏ để cấu hình và để hủy đúng.

### Ví von cho dễ hiểu

| Thành phần | Ví von | Vai trò |
|---|---|---|
| **AlarmManager** | Ông thư ký của hệ điều hành | Bạn đưa "tờ hẹn": *"6h45 gọi tôi, đưa lại tờ này"* rồi đi ngủ. Hệ thống canh giúp, đúng giờ đánh thức máy và trả lại tờ hẹn. |
| **PendingIntent** | Tờ hẹn (có ủy quyền) | Ghi sẵn "phải làm gì" (chạy receiver) + "dữ liệu kèm theo" (thông tin ca). |
| **SharedPreferences** | Cuốn sổ tay nhỏ của app | Ghi vài dòng cấu hình dạng *key: value*, không mất khi tắt app. |

### 3 kho dữ liệu xuyên suốt

| Kho | Ai giữ | Lưu gì |
|---|---|---|
| **SharedPreferences** | App | `enabled` (bật/tắt), `reminder_minutes` (số phút nhắc), `scheduled_shift_ids` (danh sách id đã hẹn — để hủy) |
| **AlarmManager** | Hệ điều hành | Thời điểm nổ + PendingIntent (kèm dữ liệu ca) của **từng** báo thức |
| **Firestore** | Server (`shifts`, `classes`, `enrollments`) | Lịch ca gốc (ngày/giờ thật) |

> App **không** copy lịch ca vào máy. Mỗi lần lên lịch, nó **đọc lại** Firestore rồi
> *tính lại* giờ nhắc.

---

## 2. Vì sao chọn AlarmManager mà không phải WorkManager?

Thông báo phải nổ **đúng một thời điểm cụ thể** (trước ca 15 phút) kể cả khi app chạy
nền/đã thoát và máy đang ở chế độ tiết kiệm pin (Doze).

- **WorkManager** được thiết kế cho tác vụ *đảm bảo chạy nhưng có thể trễ*, gom nhóm,
  chu kỳ tối thiểu 15 phút và **cố tình không chính xác** về thời điểm → không hợp mốc
  "trước đúng N phút".
- **AlarmManager** với `setExactAndAllowWhileIdle` + `BroadcastReceiver` là đúng công cụ
  cho nhắc nhở theo thời điểm chính xác, đánh thức được máy khỏi Doze.

> Báo thức bị xóa sau khi khởi động lại máy → có thêm `BootReceiver` để đặt lại.

---

## 3. Các thành phần & file

| File | Vai trò |
|---|---|
| `utils/NotificationScheduler.java` | Lên lịch / hủy / `rescheduleAll` bằng AlarmManager |
| `utils/NotificationHelper.java` | Tạo kênh (channel), kiểm tra quyền, hiển thị thông báo; các khóa extras |
| `utils/NotificationPrefs.java` | SharedPreferences: bật/tắt, số phút, danh sách id |
| `utils/NotificationSupport.java` | Xin quyền `POST_NOTIFICATIONS` (Android 13+) |
| `receivers/ShiftReminderReceiver.java` | Nhận báo thức đến giờ → kiểm tra → hiển thị |
| `receivers/BootReceiver.java` | Đặt lại lịch sau khi reboot |
| `ProfileFragment` + `fragment_profile.xml` | Giao diện Cài đặt (công tắc + chọn số phút) |
| `FirebaseRepository` | `getUpcomingShiftsForStudent/Teacher` |
| `AttendanceUtils` | `getShiftStartTimestamp` (tính giờ bắt đầu ca) |

---

## 4. Khởi tạo — KHI NÀO & CÁI GÌ?

3 mốc khởi tạo tự động, không cần người dùng bấm gì.

### Mốc A — App vừa mở: tạo "kênh thông báo"

`AttendanceApplication.onCreate()` chạy đầu tiên:

```java
@Override
public void onCreate() {
    super.onCreate();
    AndroidThreeTen.init(this);
    FirebaseApp.initializeApp(this);
    NotificationHelper.ensureChannel(this); // ← tạo KÊNH thông báo
}
```

`ensureChannel()` tạo một `NotificationChannel` — bắt buộc từ Android 8 mới hiện được:

```java
public static void ensureChannel(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {        // Android 8+
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,                        // "shift_reminders" (mã kênh cố định)
                CHANNEL_NAME,                      // "Nhắc lịch học" (tên user thấy)
                NotificationManager.IMPORTANCE_HIGH);   // ưu tiên cao → nổi + rung
        channel.setDescription("Thông báo nhắc trước khi ca học/ca dạy bắt đầu");
        channel.enableVibration(true);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);  // đăng ký kênh
    }
}
```

> **Khởi tạo cái gì:** một "ống dẫn" tên `shift_reminders`. Mọi thông báo sau này đi qua
> ống này. Gọi lại nhiều lần vô hại.

### Mốc B — Vào màn hình chính: xin quyền

`StudentMainActivity` / `TeacherMainActivity` trong `onCreate()`:

```java
NotificationSupport.requestPostPermissionIfNeeded(this);  // xin POST_NOTIFICATIONS (Android 13+)
```

```java
public static void requestPostPermissionIfNeeded(Activity activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;   // <13: không cần
    if (!new NotificationPrefs(activity).isEnabled()) return;           // đang tắt: khỏi xin
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return;              // đã có quyền
    ActivityCompat.requestPermissions(activity,                        // hiện hộp thoại xin quyền
            new String[]{Manifest.permission.POST_NOTIFICATIONS},
            REQUEST_CODE_POST_NOTIFICATIONS);
}
```

### Mốc C — Vào/quay lại app: đặt lịch báo

Cũng trong Main activity, `onStart()`:

```java
@Override
protected void onStart() {
    super.onStart();
    NotificationScheduler.rescheduleAll(this);  // đọc Firestore & đặt lại toàn bộ alarm
}
```

```
App mở   → ensureChannel()               (tạo ống dẫn thông báo)
Vào Main → xin quyền + rescheduleAll()   (đặt lịch lần đầu)
```

---

## 5. Khi BẬT thông báo — xử lý gì?

Người dùng gạt công tắc ON trong `ProfileFragment`:

```java
switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
    notiPrefs.setEnabled(isChecked);          // (1) ghi "bật=true" vào sổ tay
    if (isChecked) {
        NotificationSupport.requestPostPermissionIfNeeded(requireActivity()); // (2) xin quyền
        NotificationScheduler.rescheduleAll(requireContext());                // (3) đặt lịch
        Toast.makeText(requireContext(), "Đã bật thông báo nhắc lịch học", ...).show();
    } else {
        NotificationScheduler.cancelAll(requireContext());                    // (nhánh TẮT)
        Toast.makeText(requireContext(), "Đã tắt thông báo nhắc lịch học", ...).show();
    }
});
```

Trọng tâm là **`rescheduleAll()`** — "trái tim" của cơ chế:

```java
public static void rescheduleAll(Context context) {
    Context app = context.getApplicationContext();
    NotificationPrefs prefs = new NotificationPrefs(app);

    cancelAll(app);                    // ① LUÔN dọn sạch lịch cũ trước
    if (!prefs.isEnabled()) return;    // ② nếu đang tắt → dừng (không tạo mới)

    FirebaseRepository repo = FirebaseRepository.getInstance();
    FirebaseUser current = repo.getCurrentUser();
    if (current == null) return;       // chưa đăng nhập → thôi
    String uid = current.getUid();

    repo.getUserProfile(uid, profile -> {                 // ③ hỏi Firestore: user là ai?
        if (profile == null) return;
        if (User.ROLE_TEACHER.equals(profile.getRole())) {
            repo.getUpcomingShiftsForTeacher(uid,         // ④a GV: lấy ca các lớp mình dạy
                    shifts -> scheduleShifts(app, shifts, User.ROLE_TEACHER), e -> {});
        } else {
            repo.getUpcomingShiftsForStudent(uid,         // ④b SV: lấy ca các lớp mình học
                    shifts -> scheduleShifts(app, shifts, User.ROLE_STUDENT), e -> {});
        }
    }, e -> {});
}
```

`scheduleShifts()` duyệt **từng ca** và đặt alarm (nhiều ca → nhiều alarm):

```java
private static void scheduleShifts(Context context, List<Shift> shifts, String role) {
    NotificationPrefs prefs = new NotificationPrefs(context);
    int minutes = prefs.getReminderMinutes();       // "nhắc trước bao nhiêu phút"
    long now = System.currentTimeMillis();
    Set<String> scheduled = new HashSet<>();

    for (Shift shift : shifts) {                     // ⑤ LẶP qua TẤT CẢ ca
        if (shift == null || shift.getShiftId() == null) continue;
        if (!isSchedulable(shift)) continue;         //   bỏ ca đã kết thúc/hủy/đang mở điểm danh
        Timestamp start = AttendanceUtils.getShiftStartTimestamp(shift);
        if (start == null) continue;
        long triggerAt = start.toDate().getTime() - (long) minutes * 60_000L;  // giờ nhắc
        if (triggerAt <= now) continue;              //   bỏ ca mà giờ nhắc đã trôi qua
        scheduleAlarm(context, shift, role, triggerAt);   // ⑥ đặt 1 alarm
        scheduled.add(shift.getShiftId());
    }
    prefs.setScheduledShiftIds(scheduled);           // ⑦ ghi danh sách id vào sổ tay
}
```

Điều kiện một ca được lên lịch (`isSchedulable`):

```java
private static boolean isSchedulable(Shift shift) {
    String status = shift.getStatus();
    if (Shift.STATUS_COMPLETED.equals(status) || Shift.STATUS_CANCELLED.equals(status)) {
        return false;                    // ca đã kết thúc/hủy → không nhắc
    }
    return !shift.isAttendanceOpened();  // đang mở điểm danh → khỏi nhắc "sắp bắt đầu"
}
```

`scheduleAlarm()` — giao "tờ hẹn" cho hệ điều hành:

```java
private static void scheduleAlarm(Context context, Shift shift, String role, long triggerAt) {
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (am == null) return;
    PendingIntent pi = buildAlarmPendingIntent(context, shift, role,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    try {
        if (canScheduleExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi); // đúng giờ, xuyên Doze
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);      // fallback: gần đúng
        }
    } catch (SecurityException e) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }
}
```

`buildAlarmPendingIntent()` — nhét dữ liệu ca vào "tờ hẹn":

```java
Intent intent = new Intent(context, ShiftReminderReceiver.class);
intent.setAction(ACTION_SHIFT_REMINDER);
intent.putExtra(EXTRA_SHIFT_ID,    shift.getShiftId());
intent.putExtra(EXTRA_CLASS_ID,    shift.getClassId());
intent.putExtra(EXTRA_CLASS_NAME,  shift.getClassName());
intent.putExtra(EXTRA_SHIFT_TITLE, shift.getTitle());
intent.putExtra(EXTRA_SHIFT_TIME,  startAt + " - " + endAt);
intent.putExtra(EXTRA_ROLE,        role);
return PendingIntent.getBroadcast(context, shift.getShiftId().hashCode(), intent, flags);
//                                          └── mã số riêng (requestCode) của alarm này
```

```
Luồng BẬT:
setEnabled(true) → xin quyền → rescheduleAll()
      → cancelAll (dọn cũ) → đọc role → lấy ca từ Firestore
      → LẶP từng ca: tính giờ nhắc → AlarmManager.setExact... → lưu id
```

---

## 6. Khi TẮT thông báo — xử lý gì?

Nhánh `else` của listener gọi `cancelAll()` (và `setEnabled(false)` đã chạy trước đó,
nên `rescheduleAll` về sau gặp `isEnabled()=false` sẽ tự dừng):

```java
public static void cancelAll(Context context) {
    Context app = context.getApplicationContext();
    NotificationPrefs prefs = new NotificationPrefs(app);
    for (String shiftId : prefs.getScheduledShiftIds()) {  // duyệt từng id đã lưu
        cancelAlarm(app, shiftId);                         // hủy alarm tương ứng
    }
    prefs.setScheduledShiftIds(new HashSet<>());           // xóa trắng danh sách
}
```

`cancelAlarm()` — dựng lại **đúng** "tờ hẹn" (cùng action + cùng mã số) rồi hủy:

```java
public static void cancelAlarm(Context context, String shiftId) {
    if (shiftId == null) return;
    AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (am == null) return;

    Intent intent = new Intent(context, ShiftReminderReceiver.class);
    intent.setAction(ACTION_SHIFT_REMINDER);               // phải khớp lúc đặt
    PendingIntent pi = PendingIntent.getBroadcast(context,
            shiftId.hashCode(),                            // ← khớp mã số → tìm đúng alarm
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    am.cancel(pi);   // hệ thống bỏ cái hẹn này
    pi.cancel();
}
```

> **Mấu chốt:** AlarmManager nhận diện alarm qua **requestCode + action**, *không* qua
> extras. Nên chỉ cần tạo lại PendingIntent với cùng `shiftId.hashCode()` + cùng action
> là "chỉ đúng mặt" được cái hẹn để hủy.

```
Luồng TẮT:
setEnabled(false) → cancelAll()
      → LẶP từng id trong sổ tay → cancelAlarm() → xóa sổ tay
      → (sau này rescheduleAll thấy enabled=false → dừng, không tạo mới)
```

---

## 7. Khi alarm KÍCH HOẠT (khép vòng)

Đến giờ, hệ điều hành đánh thức máy và gửi "tờ hẹn" tới `ShiftReminderReceiver`. Nếu app
đã bị thoát hẳn, hệ thống **tự khởi động lại tiến trình app** (chạy `Application.onCreate`
→ khởi tạo Firebase) rồi mới gọi receiver.

```java
@Override
public void onReceive(Context context, Intent intent) {
    NotificationPrefs prefs = new NotificationPrefs(context.getApplicationContext());
    if (!prefs.isEnabled()) return;                    // kiểm tra lại: còn bật không?

    String shiftId   = intent.getStringExtra(EXTRA_SHIFT_ID);   // lấy dữ liệu từ "tờ hẹn"
    String classId   = intent.getStringExtra(EXTRA_CLASS_ID);
    String className  = intent.getStringExtra(EXTRA_CLASS_NAME);
    String title      = intent.getStringExtra(EXTRA_SHIFT_TITLE);
    String time       = intent.getStringExtra(EXTRA_SHIFT_TIME);
    String role       = intent.getStringExtra(EXTRA_ROLE);
    if (shiftId == null) return;

    final PendingResult pending = goAsync();           // xin thêm thời gian để đọc mạng
    FirebaseRepository repo = FirebaseRepository.getInstance();
    repo.getShiftById(shiftId,
        shift -> { try { if (shouldNotify(shift)) showReminder(...); } finally { pending.finish(); } },
        e -> { try { showReminder(...); } finally { pending.finish(); } }); // lỗi → vẫn hiện (fail-open)
}
```

`shouldNotify()` kiểm tra ca còn hợp lệ (chưa kết thúc/hủy, chưa quá giờ kết thúc), rồi
`showReminder()` dựng notification + PendingIntent mở đúng màn hình theo `role`:

- **Sinh viên** → `ShiftDetailActivity` (có nút "Điểm danh ngay").
- **Giảng viên** → `ShiftAttendanceListActivity` (màn hình quản lý/điểm danh).

Dùng `TaskStackBuilder` để nhấn Back từ màn chi tiết quay về màn hình chính của app.

---

## 8. Ví dụ luồng đầy đủ (có số liệu)

**Bối cảnh:** SV Nam học lớp "Lập trình Android", buổi **05/07/2026, 07:00–09:30**;
cài đặt: bật thông báo, nhắc trước **15 phút**. Bây giờ là **tối 04/07 lúc 20:00**, Nam mở app.

```
20:00 (04/07)  Nam MỞ APP
   │  StudentMainActivity.onStart() → rescheduleAll()
   │    ① đọc sổ tay: enabled=true, minutes=15 → hủy alarm cũ
   │    ② hỏi Firestore: role=student, có buổi CLASS01_2026-07-05
   │    ③ tính giờ nhắc: 07:00 − 15 phút = 06:45 (05/07)
   │       06:45 mai  >  20:00 nay  → HỢP LỆ
   │    ④ AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, [06:45 05/07], pi)
   │       pi = {chạy ShiftReminderReceiver; kèm className='Lập trình Android',
   │             time='07:00 - 09:30', role='student'}; mã số = shiftId.hashCode()
   │    ⑤ ghi sổ tay: scheduled_shift_ids = [CLASS01_2026-07-05, ...]
   ▼
✅ Nam tắt app, khóa máy đi ngủ. App KHÔNG chạy nữa — hệ điều hành tự canh.

06:45 (05/07)  Chuông reo
   │  Hệ điều hành đánh thức CPU → (nếu app đã chết) khởi động lại tiến trình
   │  → gửi broadcast + "tờ hẹn" tới ShiftReminderReceiver.onReceive()
   │    - còn bật? OK
   │    - đọc lại Firestore: ca còn hợp lệ? OK
   │    - hiển thị: 🔔 "Sắp đến giờ học — Lập trình Android • 07:00 - 09:30"
   ▼
Nam bấm vào → mở ShiftDetailActivity → "Điểm danh ngay".
```

---

## 9. Khi nào lịch được cập nhật? (xóa ca / thêm ca bù)

Lịch chỉ được đồng bộ khi **`rescheduleAll()` chạy**, tức tại 3 thời điểm:
1. **Mở / quay lại app** (`onStart` của Main activity).
2. **Bật lại thông báo** hoặc **đổi số phút nhắc** (ProfileFragment).
3. **Sau khi khởi động lại máy** (`BootReceiver`).

Vì **không có push/realtime**, giữa hai lần đó app không tự biết dữ liệu Firestore đổi.

- **Trên máy người tự thao tác (GV):** nếu thao tác mở một Activity khác rồi back về
  Main → `onStart` chạy → khớp ngay. Nếu thao tác nằm gọn trong một dialog/fragment
  (không rời activity) → phải đợi lần thoát rồi mở lại app.
- **Trên máy người khác (SV):** chỉ cập nhật ở **lần mở app kế tiếp** của SV.
  - *Thêm ca bù*: SV chưa có báo cho tới khi mở lại app.
  - *Xóa ca*: alarm cũ vẫn chờ; tới giờ receiver đọc lại Firestore — nếu ca chuyển
    `completed`/`cancelled` thì bỏ qua; nếu ca **bị xóa hẳn**, hiện tại code fail-open
    nên **có thể vẫn hiện nhầm 1 lần** (hạn chế đã biết).

### Hướng cải thiện (tùy chọn)
1. Gọi `rescheduleAll()` **ngay sau mỗi thao tác đổi lịch** (xóa ca, tạo ca bù, dời ca,
   đóng phiên) — vá triệt để trường hợp dialog-trong-fragment.
2. Sửa receiver **không fail-open khi ca bị xóa** (phân biệt "not found" với lỗi mạng).
3. Thêm **realtime listener khi app ở foreground** để đặt lại sống (chỉ khi app đang mở).

---

## 10. Cấu hình liên quan

### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver android:name=".receivers.ShiftReminderReceiver" android:exported="false" />
<receiver android:name=".receivers.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

### SharedPreferences (`notification_prefs`)
| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `enabled` | boolean | `true` | Bật/tắt thông báo |
| `reminder_minutes` | int | `15` | Nhắc trước bao nhiêu phút |
| `scheduled_shift_ids` | Set&lt;String&gt; | rỗng | Danh sách shiftId đang hẹn (để hủy) |

### Quyền báo thức chính xác
- Android ≤ 12: `SCHEDULE_EXACT_ALARM` được cấp mặc định → dùng `setExactAndAllowWhileIdle`.
- Android 13+: nếu `canScheduleExactAlarms()` trả `false` → tự lùi về `setAndAllowWhileIdle`
  (vẫn nổ trong Doze nhưng có thể trễ vài phút). Không gây crash.

---

## 11. Bản đồ tổng thể

```
┌── KHỞI TẠO ─────────────────────────────────────────────┐
│ App mở      → ensureChannel()      (tạo ống dẫn)         │
│ Vào Main    → xin quyền + rescheduleAll()               │
└─────────────────────────────────────────────────────────┘
        │
┌── BẬT ───────────────────┐     ┌── TẮT ──────────────────┐
│ setEnabled(true)         │     │ setEnabled(false)        │
│ rescheduleAll():         │     │ cancelAll():             │
│   cancelAll (dọn)        │     │   duyệt id → cancelAlarm │
│   đọc Firestore          │     │   xóa sổ tay             │
│   LẶP ca → setExact...   │     │ (không tạo mới nữa)      │
│   lưu id vào sổ tay      │     └──────────────────────────┘
└──────────────────────────┘
        │ (tới giờ)
        ▼
  Hệ điều hành → ShiftReminderReceiver → kiểm tra → show() → 🔔 → tap → mở màn hình
```

**Một câu tóm gọn:** *Firestore giữ lịch ca → app tính giờ nhắc → AlarmManager (hệ điều
hành) giữ báo thức + dữ liệu ca trong PendingIntent → tới giờ đánh thức receiver để hiện
thông báo. SharedPreferences chỉ giữ công tắc, số phút, và danh sách id để dọn dẹp.*
