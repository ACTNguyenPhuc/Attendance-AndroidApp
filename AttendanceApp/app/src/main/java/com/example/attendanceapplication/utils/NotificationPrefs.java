package com.example.attendanceapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Lưu trạng thái tính năng thông báo cục bộ bằng {@link SharedPreferences}:
 * bật/tắt, số phút nhắc trước ca học và danh sách shiftId đang được lên lịch báo.
 *
 * <p>Danh sách shiftId được giữ để có thể HỦY đúng các báo thức (alarm) đã đặt khi
 * người dùng tắt thông báo hoặc khi lịch học thay đổi — vì {@code AlarmManager}
 * không cho liệt kê các alarm đang chờ.
 */
public class NotificationPrefs {

    private static final String PREFS = "notification_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_REMINDER_MINUTES = "reminder_minutes";
    private static final String KEY_SCHEDULED_IDS = "scheduled_shift_ids";

    /** Mặc định nhắc trước ca học 15 phút theo yêu cầu. */
    public static final int DEFAULT_REMINDER_MINUTES = 15;

    private final SharedPreferences sp;

    public NotificationPrefs(Context context) {
        sp = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Mặc định BẬT để tính năng hoạt động ngay sau khi cài đặt. */
    public boolean isEnabled() {
        return sp.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public int getReminderMinutes() {
        return sp.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES);
    }

    public void setReminderMinutes(int minutes) {
        sp.edit().putInt(KEY_REMINDER_MINUTES, minutes).apply();
    }

    /**
     * Trả về BẢN SAO tập shiftId đang được lên lịch. Luôn sao chép vì
     * {@link SharedPreferences#getStringSet} trả về set không được phép sửa trực tiếp.
     */
    public Set<String> getScheduledShiftIds() {
        return new HashSet<>(sp.getStringSet(KEY_SCHEDULED_IDS, new HashSet<>()));
    }

    public void setScheduledShiftIds(Set<String> shiftIds) {
        sp.edit().putStringSet(KEY_SCHEDULED_IDS, new HashSet<>(shiftIds)).apply();
    }
}
