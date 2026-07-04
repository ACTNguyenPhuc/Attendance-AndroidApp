package com.example.attendanceapplication.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.TaskStackBuilder;

import com.example.attendanceapplication.activities.ShiftAttendanceListActivity;
import com.example.attendanceapplication.activities.ShiftDetailActivity;
import com.example.attendanceapplication.activities.StudentMainActivity;
import com.example.attendanceapplication.activities.TeacherMainActivity;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.example.attendanceapplication.utils.NotificationHelper;
import com.example.attendanceapplication.utils.NotificationPrefs;
import com.google.firebase.Timestamp;

/**
 * Nhận sự kiện "đến giờ nhắc" do {@code AlarmManager} bắn tới rồi hiển thị thông báo cục bộ.
 *
 * <p>Trước khi hiển thị còn kiểm tra lại các điều kiện của yêu cầu: người dùng vẫn bật
 * thông báo, ca học chưa kết thúc và phiên điểm danh chưa bị đóng (đọc lại bản mới nhất
 * trên Firestore). Việc đọc lại chạy bất đồng bộ nên dùng {@link #goAsync()} để giữ tiến
 * trình sống trong lúc chờ; nếu đọc lỗi thì vẫn hiển thị (fail-open) dựa trên dữ liệu đã
 * lên lịch để tránh bỏ sót nhắc nhở khi mất mạng.
 */
public class ShiftReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        NotificationPrefs prefs = new NotificationPrefs(app);
        // Điều kiện: người dùng đã bật chức năng nhận thông báo.
        if (!prefs.isEnabled()) return;

        String shiftId   = intent.getStringExtra(NotificationHelper.EXTRA_SHIFT_ID);
        String classId   = intent.getStringExtra(NotificationHelper.EXTRA_CLASS_ID);
        String className = intent.getStringExtra(NotificationHelper.EXTRA_CLASS_NAME);
        String title     = intent.getStringExtra(NotificationHelper.EXTRA_SHIFT_TITLE);
        String time      = intent.getStringExtra(NotificationHelper.EXTRA_SHIFT_TIME);
        String role      = intent.getStringExtra(NotificationHelper.EXTRA_ROLE);
        if (shiftId == null) return;

        final PendingResult pending = goAsync();
        FirebaseRepository repo = FirebaseRepository.getInstance();
        repo.getShiftById(shiftId,
                shift -> {
                    try {
                        if (shouldNotify(shift)) {
                            showReminder(app, shiftId, classId, className, title, time, role);
                        }
                    } finally {
                        pending.finish();
                    }
                },
                e -> {
                    // Fail-open: không đọc được thì vẫn nhắc theo dữ liệu đã lên lịch.
                    try {
                        showReminder(app, shiftId, classId, className, title, time, role);
                    } finally {
                        pending.finish();
                    }
                });
    }

    /** Ca còn hợp lệ để nhắc: chưa kết thúc/hủy (phiên chưa đóng) và chưa quá giờ kết thúc. */
    private boolean shouldNotify(Shift shift) {
        if (shift == null) return false;
        String status = shift.getStatus();
        if (Shift.STATUS_COMPLETED.equals(status) || Shift.STATUS_CANCELLED.equals(status)) {
            return false;
        }
        Timestamp end = AttendanceUtils.getShiftEndTimestamp(shift);
        return end == null || end.toDate().getTime() > System.currentTimeMillis();
    }

    private void showReminder(Context context, String shiftId, String classId, String className,
                              String title, String time, String role) {
        boolean isTeacher = User.ROLE_TEACHER.equals(role);

        Intent parentIntent;
        Intent targetIntent;
        if (isTeacher) {
            parentIntent = new Intent(context, TeacherMainActivity.class);
            targetIntent = new Intent(context, ShiftAttendanceListActivity.class);
            targetIntent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_ID, shiftId);
            targetIntent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_ID, classId);
            targetIntent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_NAME, className);
            targetIntent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TITLE, title);
            targetIntent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TIME, time);
        } else {
            parentIntent = new Intent(context, StudentMainActivity.class);
            targetIntent = new Intent(context, ShiftDetailActivity.class);
            targetIntent.putExtra(ShiftDetailActivity.EXTRA_SHIFT_ID, shiftId);
            targetIntent.putExtra(ShiftDetailActivity.EXTRA_CLASS_ID, classId);
            targetIntent.putExtra(ShiftDetailActivity.EXTRA_CLASS_NAME, className);
        }

        // Dựng back stack: nhấn Back từ màn hình chi tiết sẽ quay về màn hình chính của app.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(parentIntent);
        stackBuilder.addNextIntent(targetIntent);
        PendingIntent contentIntent = stackBuilder.getPendingIntent(shiftId.hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String heading = isTeacher ? "Sắp đến giờ dạy" : "Sắp đến giờ học";
        StringBuilder body = new StringBuilder();
        body.append(className != null && !className.trim().isEmpty() ? className : "Buổi học");
        if (time != null && !time.trim().isEmpty()) body.append(" • ").append(time);
        body.append(isTeacher
                ? "\nCa dạy sắp bắt đầu. Nhấn để mở màn hình điểm danh."
                : "\nCa học sắp bắt đầu. Nhấn để xem chi tiết và điểm danh.");

        NotificationHelper.show(context, shiftId.hashCode(), heading, body.toString(), contentIntent);
    }
}
