package com.example.attendanceapplication.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Tiện ích nhỏ dùng chung để xin quyền {@code POST_NOTIFICATIONS} (Android 13+) mà không
 * lặp lại code ở nhiều màn hình. Trên Android 12 trở xuống không cần quyền runtime này.
 */
public class NotificationSupport {

    public static final int REQUEST_CODE_POST_NOTIFICATIONS = 4001;

    private NotificationSupport() {}

    /**
     * Xin quyền hiển thị thông báo nếu tính năng đang bật mà chưa được cấp quyền.
     * An toàn để gọi nhiều lần: hệ thống chỉ hiện hộp thoại khi thực sự cần.
     */
    public static void requestPostPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (!new NotificationPrefs(activity).isEnabled()) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_POST_NOTIFICATIONS);
    }
}
