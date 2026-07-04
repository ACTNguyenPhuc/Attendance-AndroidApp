package com.example.attendanceapplication.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.attendanceapplication.utils.NotificationScheduler;

/**
 * Lên lịch lại toàn bộ thông báo sau khi thiết bị khởi động lại — các báo thức của
 * {@code AlarmManager} bị xóa sau mỗi lần reboot nên cần đặt lại để nhắc nhở vẫn hoạt động.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            NotificationScheduler.rescheduleAll(context);
        }
    }
}
