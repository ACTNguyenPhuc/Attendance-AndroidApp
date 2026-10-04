package com.example.attendanceapplication.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.SplashActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Dựng khung widget "Lịch hôm nay" và điều phối việc làm mới. */
public final class TodayShiftsWidget {

    static final String ACTION_REFRESH =
            "com.example.attendanceapplication.widget.ACTION_REFRESH";
    private static final int REQ_REFRESH = 1;
    private static final int REQ_ALARM = 2;
    private static final int REQ_OPEN_APP = 3;
    private static final int REQ_ITEM = 4;
    /** Cho hệ thống gom báo thức trong khoảng này để tiết kiệm pin. */
    private static final long ALARM_WINDOW_MS = 60_000L;

    private TodayShiftsWidget() {}

    /** Gọi sau khi dữ liệu thay đổi trong app (mở/đóng phiên, điểm danh, đăng nhập/xuất). */
    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = widgetIds(app, mgr);
        if (ids.length == 0) return;
        for (int id : ids) mgr.updateAppWidget(id, buildFrame(app, id));
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }

    static int[] widgetIds(Context app, AppWidgetManager mgr) {
        return mgr.getAppWidgetIds(new ComponentName(app, TodayShiftsWidgetProvider.class));
    }

    static RemoteViews buildFrame(Context app, int appWidgetId) {
        RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_today_shifts);
        views.setTextViewText(R.id.widget_subtitle, todayLabel());

        Intent service = new Intent(app, TodayShiftsWidgetService.class);
        service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // Data riêng cho mỗi widget để hệ thống không gộp chung các factory.
        service.setData(Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, service);
        views.setEmptyView(R.id.widget_list, R.id.widget_empty);

        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(app, REQ_REFRESH));

        Intent open = new Intent(app, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        views.setOnClickPendingIntent(R.id.widget_title_area, PendingIntent.getActivity(
                app, REQ_OPEN_APP, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Mẫu chung cho mọi card; từng card/nút gắn thêm extras qua fill-in intent,
        // nên PendingIntent này bắt buộc phải MUTABLE.
        Intent template = new Intent(app, WidgetActionActivity.class);
        views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(
                app, REQ_ITEM, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        return views;
    }

    /** Đổi dòng chữ hiển thị khi danh sách trống (đang tải, chưa đăng nhập, lỗi...). */
    static void showMessage(Context app, String message) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        RemoteViews partial = new RemoteViews(app.getPackageName(), R.layout.widget_today_shifts);
        partial.setTextViewText(R.id.widget_empty, message);
        for (int id : widgetIds(app, mgr)) mgr.partiallyUpdateAppWidget(id, partial);
    }

    /**
     * Hẹn lần làm mới kế tiếp (lúc một ca bắt đầu/kết thúc hoặc qua ngày mới) để
     * trạng thái card tự chuyển mà không cần mở app. Báo thức không đánh thức máy.
     */
    static void scheduleRefreshAt(Context app, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setWindow(AlarmManager.RTC, triggerAtMillis, ALARM_WINDOW_MS,
                refreshIntent(app, REQ_ALARM));
    }

    static void cancelScheduledRefresh(Context app) {
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(app, REQ_ALARM));
    }

    private static PendingIntent refreshIntent(Context app, int requestCode) {
        Intent intent = new Intent(app, TodayShiftsWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(app, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Ví dụ "Thứ 7, 04/10". */
    static String todayLabel() {
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        String day = dow == Calendar.SUNDAY ? "Chủ nhật" : "Thứ " + dow;
        return day + ", " + new SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.getTime());
    }
}
