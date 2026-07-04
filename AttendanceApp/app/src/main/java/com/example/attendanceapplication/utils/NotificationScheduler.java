package com.example.attendanceapplication.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.receivers.ShiftReminderReceiver;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bộ lên lịch thông báo cục bộ nhắc trước ca học bằng {@link AlarmManager}.
 *
 * <p><b>Vì sao AlarmManager mà không phải WorkManager?</b> Thông báo phải nổ ĐÚNG một
 * thời điểm cụ thể (ví dụ trước ca 15 phút) kể cả khi app chạy nền/đã bị thoát và máy
 * đang ở chế độ Doze. WorkManager được thiết kế cho tác vụ "đảm bảo chạy nhưng có thể
 * trễ", gom nhóm, chu kỳ tối thiểu 15 phút và cố tình KHÔNG chính xác về thời điểm, nên
 * không phù hợp với mốc "trước đúng N phút". {@code setExactAndAllowWhileIdle} +
 * {@link ShiftReminderReceiver} là đúng công cụ cho nhắc nhở theo thời điểm chính xác.
 *
 * <p>Mỗi báo thức có mã (request code) duy nhất suy ra từ {@code shiftId} nên không bị
 * trùng/đè sai. Tập shiftId đang lên lịch được lưu trong {@link NotificationPrefs} để
 * hủy chính xác khi tắt thông báo hoặc khi lịch thay đổi.
 */
public class NotificationScheduler {

    private static final String TAG = "NotificationScheduler";
    public static final String ACTION_SHIFT_REMINDER =
            "com.example.attendanceapplication.action.SHIFT_REMINDER";

    private NotificationScheduler() {}

    /**
     * Hủy toàn bộ báo thức đã lên lịch (khi người dùng TẮT thông báo) và xóa danh sách theo dõi.
     */
    public static void cancelAll(Context context) {
        Context app = context.getApplicationContext();
        NotificationPrefs prefs = new NotificationPrefs(app);
        for (String shiftId : prefs.getScheduledShiftIds()) {
            cancelAlarm(app, shiftId);
        }
        prefs.setScheduledShiftIds(new HashSet<>());
    }

    /**
     * Lên lịch lại TOÀN BỘ thông báo cho người dùng đang đăng nhập: luôn hủy lịch cũ
     * trước rồi mới đặt lại theo dữ liệu mới nhất. Gọi khi mở app, khi bật lại thông báo,
     * khi đổi thời gian nhắc, sau khi khởi động lại máy hoặc khi lịch học thay đổi.
     *
     * <p>Nếu thông báo đang tắt thì chỉ hủy lịch cũ và dừng lại (không tạo mới).
     */
    public static void rescheduleAll(Context context) {
        Context app = context.getApplicationContext();
        NotificationPrefs prefs = new NotificationPrefs(app);

        // Luôn dọn lịch cũ trước để tránh nhắc theo lịch đã thay đổi.
        cancelAll(app);
        if (!prefs.isEnabled()) return;

        FirebaseRepository repo = FirebaseRepository.getInstance();
        FirebaseUser current = repo.getCurrentUser();
        if (current == null) return;
        String uid = current.getUid();

        repo.getUserProfile(uid, profile -> {
            if (profile == null) return;
            if (User.ROLE_TEACHER.equals(profile.getRole())) {
                repo.getUpcomingShiftsForTeacher(uid,
                        shifts -> scheduleShifts(app, shifts, User.ROLE_TEACHER),
                        e -> Log.w(TAG, "Không lấy được ca dạy để lên lịch: " + e.getMessage()));
            } else {
                repo.getUpcomingShiftsForStudent(uid,
                        shifts -> scheduleShifts(app, shifts, User.ROLE_STUDENT),
                        e -> Log.w(TAG, "Không lấy được ca học để lên lịch: " + e.getMessage()));
            }
        }, e -> Log.w(TAG, "Không lấy được hồ sơ người dùng: " + e.getMessage()));
    }

    private static void scheduleShifts(Context context, List<Shift> shifts, String role) {
        NotificationPrefs prefs = new NotificationPrefs(context);
        int minutes = prefs.getReminderMinutes();
        long now = System.currentTimeMillis();
        Set<String> scheduled = new HashSet<>();

        for (Shift shift : shifts) {
            if (shift == null || shift.getShiftId() == null) continue;
            if (!isSchedulable(shift)) continue;

            Timestamp start = AttendanceUtils.getShiftStartTimestamp(shift);
            if (start == null) continue;

            long triggerAt = start.toDate().getTime() - (long) minutes * 60_000L;
            // Điều kiện: thời điểm nhắc vẫn nằm trong tương lai.
            if (triggerAt <= now) continue;

            scheduleAlarm(context, shift, role, triggerAt);
            scheduled.add(shift.getShiftId());
        }

        prefs.setScheduledShiftIds(scheduled);
        Log.d(TAG, "Đã lên lịch " + scheduled.size() + " thông báo (" + role + ")");
    }

    /** Ca hợp lệ để nhắc: chưa kết thúc/hủy và chưa mở điểm danh (chưa cần nhắc nữa). */
    private static boolean isSchedulable(Shift shift) {
        String status = shift.getStatus();
        if (Shift.STATUS_COMPLETED.equals(status) || Shift.STATUS_CANCELLED.equals(status)) {
            return false;
        }
        // attendanceOpened=true nghĩa là phiên đang mở -> không cần nhắc "sắp bắt đầu".
        return !shift.isAttendanceOpened();
    }

    private static void scheduleAlarm(Context context, Shift shift, String role, long triggerAt) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildAlarmPendingIntent(context, shift, role,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (canScheduleExact(am)) {// Có quyền báo thức chính xác -> không bị trễ.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                // Không có quyền báo thức chính xác -> vẫn nổ được trong Doze nhưng có thể trễ chút.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    /** Hủy báo thức của một ca học cụ thể (dùng khi lịch ca đó thay đổi/hủy). */
    public static void cancelAlarm(Context context, String shiftId) {
        if (shiftId == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // PendingIntent để hủy chỉ cần khớp component + action + requestCode (không tính extras).
        Intent intent = new Intent(context, ShiftReminderReceiver.class);
        intent.setAction(ACTION_SHIFT_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, shiftId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        pi.cancel();
    }

    private static PendingIntent buildAlarmPendingIntent(Context context, Shift shift,
                                                         String role, int flags) {
        Intent intent = new Intent(context, ShiftReminderReceiver.class);
        intent.setAction(ACTION_SHIFT_REMINDER);
        intent.putExtra(NotificationHelper.EXTRA_SHIFT_ID, shift.getShiftId());
        intent.putExtra(NotificationHelper.EXTRA_CLASS_ID, shift.getClassId());
        intent.putExtra(NotificationHelper.EXTRA_CLASS_NAME, shift.getClassName());
        intent.putExtra(NotificationHelper.EXTRA_SHIFT_TITLE, shift.getTitle());
        intent.putExtra(NotificationHelper.EXTRA_SHIFT_TIME,
                safe(shift.getStartAt()) + " - " + safe(shift.getEndAt()));
        intent.putExtra(NotificationHelper.EXTRA_ROLE, role);
        return PendingIntent.getBroadcast(context, shift.getShiftId().hashCode(), intent, flags);
    }

    private static boolean canScheduleExact(AlarmManager am) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return am.canScheduleExactAlarms();
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
