package com.example.attendanceapplication.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import com.example.attendanceapplication.R;

/** Widget màn hình chính "Lịch hôm nay" cho cả giảng viên và sinh viên. */
public class TodayShiftsWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        Context app = context.getApplicationContext();
        for (int id : appWidgetIds) {
            mgr.updateAppWidget(id, TodayShiftsWidget.buildFrame(app, id));
        }
        mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (TodayShiftsWidget.ACTION_REFRESH.equals(intent.getAction())) {
            TodayShiftsWidget.refresh(context);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onDisabled(Context context) {
        TodayShiftsWidget.cancelScheduledRefresh(context.getApplicationContext());
    }
}
