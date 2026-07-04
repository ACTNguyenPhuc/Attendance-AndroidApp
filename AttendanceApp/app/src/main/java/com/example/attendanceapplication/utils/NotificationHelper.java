package com.example.attendanceapplication.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;

/**
 * Tập trung việc tạo {@link NotificationChannel}, kiểm tra quyền và HIỂN THỊ thông báo
 * cục bộ nhắc lịch học. Dùng {@link NotificationCompat} để tương thích nhiều phiên bản
 * Android và {@link NotificationChannel} cho Android 8 (API 26) trở lên.
 */
public class NotificationHelper {

    public static final String CHANNEL_ID = "shift_reminders";
    public static final String CHANNEL_NAME = "Nhắc lịch học";

    // Khóa dữ liệu truyền kèm qua Intent của alarm -> receiver -> màn hình đích.
    public static final String EXTRA_SHIFT_ID    = "noti_shift_id";
    public static final String EXTRA_CLASS_ID    = "noti_class_id";
    public static final String EXTRA_CLASS_NAME  = "noti_class_name";
    public static final String EXTRA_SHIFT_TITLE = "noti_shift_title";
    public static final String EXTRA_SHIFT_TIME  = "noti_shift_time";
    public static final String EXTRA_ROLE        = "noti_role";

    private NotificationHelper() {}

    /** Tạo kênh thông báo (chỉ có tác dụng từ Android 8 trở lên). Gọi lại nhiều lần vô hại. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH); //Khởi tạo thông báo với độ ưu tiên cao.
            channel.setDescription("Thông báo nhắc trước khi ca học/ca dạy bắt đầu");
            channel.enableVibration(true); // Hiện thông báo có âm thanh.
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);//Đăng ký kênh thông báo.
        }
    }

    /**
     * Đã đủ điều kiện hiển thị thông báo chưa: từ Android 13 (TIRAMISU) cần quyền
     * runtime {@code POST_NOTIFICATIONS}; phiên bản cũ hơn thì phụ thuộc việc người
     * dùng có tắt thông báo của app trong Cài đặt hệ thống hay không.
     */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Dựng và hiển thị một thông báo nhắc lịch học với {@code contentIntent} khi nhấn vào. */
    public static void show(Context context, int notificationId, String title, String text,
                            PendingIntent contentIntent) {
        ensureChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        if (!canPostNotifications(context)) return;
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }
}
