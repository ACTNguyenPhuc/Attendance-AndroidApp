package com.example.attendanceapplication.widget;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Cung cấp danh sách card buổi học cho ListView của widget. */
public class TodayShiftsWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsFactory {
        private static final String TAG = "TodayShiftsWidget";
        /** Khi đang có phiên mở, làm mới định kỳ để số sinh viên có mặt không bị cũ. */
        private static final long LIVE_REFRESH_MS = 5 * 60_000L;

        private final Context app;
        private final List<Row> rows = new ArrayList<>();
        private boolean teacher;

        static class Row {
            Shift shift;
            ShiftWidgetState state;
        }

        Factory(Context app) {
            this.app = app;
        }

        @Override public void onCreate() {}

        /** Chạy trên luồng nền của hệ thống nên được phép chờ Firestore. */
        @Override
        public void onDataSetChanged() {
            Date now = new Date();
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now);
            TodayWidgetData data;
            try {
                data = FirebaseRepository.getInstance().loadTodayWidgetDataBlocking(today);
            } catch (Exception e) {
                Log.w(TAG, "Không tải được dữ liệu widget", e);
                // Giữ danh sách cũ (nếu có) để widget không trống trơn khi mất mạng.
                TodayShiftsWidget.showMessage(app, "Không tải được dữ liệu. Bấm ⟳ để thử lại.");
                TodayShiftsWidget.scheduleRefreshAt(app, now.getTime() + LIVE_REFRESH_MS);
                return;
            }

            rows.clear();
            if (data == null) {
                TodayShiftsWidget.showMessage(app, "Đăng nhập ứng dụng để xem lịch hôm nay.");
                TodayShiftsWidget.cancelScheduledRefresh(app);
                return;
            }
            teacher = data.teacher;

            List<Shift> shifts = new ArrayList<>(data.shifts);
            shifts.sort((a, b) -> safe(a.getStartAt()).compareTo(safe(b.getStartAt())));
            boolean anyLive = false;
            for (Shift s : shifts) {
                Row row = new Row();
                row.shift = s;
                if (teacher) {
                    row.state = ShiftWidgetState.forTeacher(s, now,
                            data.rosterByClass.getOrDefault(s.getClassId(), 0),
                            data.presentByShift.getOrDefault(s.getShiftId(), 0));
                } else {
                    Attendance mine = data.mineByShift.get(s.getShiftId());
                    row.state = ShiftWidgetState.forStudent(s, now, mine, checkinText(mine));
                }
                anyLive |= row.state.kind == ShiftWidgetState.Kind.LIVE;
                rows.add(row);
            }
            if (rows.isEmpty()) {
                TodayShiftsWidget.showMessage(app, "Hôm nay không có buổi học.");
            }
            TodayShiftsWidget.scheduleRefreshAt(app, nextRefreshAt(shifts, now, anyLive));
        }

        /** Mốc gần nhất trong: giờ bắt đầu/kết thúc ca, 5 phút nữa (nếu có phiên mở), nửa đêm. */
        private static long nextRefreshAt(List<Shift> shifts, Date now, boolean anyLive) {
            Calendar midnight = Calendar.getInstance();
            midnight.setTime(now);
            midnight.add(Calendar.DAY_OF_MONTH, 1);
            midnight.set(Calendar.HOUR_OF_DAY, 0);
            midnight.set(Calendar.MINUTE, 0);
            midnight.set(Calendar.SECOND, 30);
            long next = midnight.getTimeInMillis();
            if (anyLive) next = Math.min(next, now.getTime() + LIVE_REFRESH_MS);
            for (Shift s : shifts) {
                for (String time : new String[]{s.getStartAt(), s.getEndAt()}) {
                    Date at = ShiftWidgetState.parse(s.getDate(), time);
                    if (at != null && at.after(now)) next = Math.min(next, at.getTime() + 5_000L);
                }
            }
            return next;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_shift_item);
            if (position < 0 || position >= rows.size()) return v;
            Row row = rows.get(position);
            Shift s = row.shift;
            ShiftWidgetState st = row.state;

            v.setInt(R.id.item_card, "setBackgroundResource",
                    st.kind == ShiftWidgetState.Kind.LIVE
                            ? R.drawable.bg_widget_card_live : R.drawable.bg_widget_card);

            v.setImageViewResource(R.id.item_state_icon, iconFor(st));
            v.setTextViewText(R.id.item_state, st.header);
            v.setTextColor(R.id.item_state, color(st.headerTone));
            if (st.headerRight != null) {
                v.setTextViewText(R.id.item_state_right, st.headerRight);
                v.setViewVisibility(R.id.item_state_right, View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.item_state_right, View.GONE);
            }

            v.setTextViewText(R.id.item_start, orDash(s.getStartAt()));
            v.setTextColor(R.id.item_start, app.getColor(
                    st.kind == ShiftWidgetState.Kind.LIVE ? R.color.widget_blue : R.color.widget_text));
            v.setTextViewText(R.id.item_end, orDash(s.getEndAt()));
            v.setTextViewText(R.id.item_class,
                    notBlank(s.getClassName()) ? s.getClassName() : orDash(s.getTitle()));
            v.setTextViewText(R.id.item_meta, meta(s));
            v.setTextViewText(R.id.item_status, st.statusLine);
            v.setTextColor(R.id.item_status, color(st.statusTone));

            if (st.buttonText != null) {
                v.setViewVisibility(R.id.item_button, View.VISIBLE);
                v.setTextViewText(R.id.item_button_text, st.buttonText);
                v.setImageViewResource(R.id.item_button_icon,
                        st.buttonAction == ShiftWidgetState.Action.SCAN
                                ? R.drawable.ic_widget_scan : R.drawable.ic_widget_clipboard);
                v.setOnClickFillInIntent(R.id.item_button, fillIn(s, st.buttonAction));
            } else {
                v.setViewVisibility(R.id.item_button, View.GONE);
            }
            if (st.footer != null) {
                v.setTextViewText(R.id.item_footer, st.footer);
                v.setViewVisibility(R.id.item_footer, View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.item_footer, View.GONE);
            }
            v.setOnClickFillInIntent(R.id.item_card, fillIn(s, st.cardAction));
            return v;
        }

        private Intent fillIn(Shift s, ShiftWidgetState.Action action) {
            Intent i = new Intent();
            i.putExtra(WidgetActionActivity.EXTRA_ACTION, action.name());
            i.putExtra(WidgetActionActivity.EXTRA_TEACHER, teacher);
            WidgetActionActivity.putShift(i, s);
            return i;
        }

        /** "Buổi 5 · Phòng A203" (giảng viên) hoặc "GV Nguyễn A · Phòng A203" (sinh viên). */
        private String meta(Shift s) {
            List<String> parts = new ArrayList<>();
            if (teacher) {
                if (notBlank(s.getTitle())) parts.add(s.getTitle().trim());
            } else if (notBlank(s.getTeacherDisplayName())) {
                parts.add("GV " + s.getTeacherDisplayName().trim());
            }
            if (s.isMakeup()) parts.add("Học bù");
            parts.add("Phòng " + (notBlank(s.getRoom()) ? s.getRoom().trim() : "--"));
            return String.join(" · ", parts);
        }

        private static int iconFor(ShiftWidgetState st) {
            switch (st.headerTone) {
                case GREEN: return R.drawable.ic_widget_done;
                case BLUE:  return R.drawable.ic_widget_live;
                case RED:   return R.drawable.ic_widget_cancel;
                default:    return R.drawable.ic_widget_clock;
            }
        }

        private int color(ShiftWidgetState.Tone tone) {
            switch (tone) {
                case GREEN:  return app.getColor(R.color.widget_green);
                case BLUE:   return app.getColor(R.color.widget_blue);
                case RED:    return app.getColor(R.color.widget_red);
                case ORANGE: return app.getColor(R.color.widget_orange);
                default:     return app.getColor(R.color.widget_gray);
            }
        }

        private static String checkinText(Attendance a) {
            if (a == null || a.getCheckinTime() == null) return null;
            return new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(a.getCheckinTime().toDate());
        }

        private static boolean notBlank(String v) { return v != null && !v.trim().isEmpty(); }
        private static String orDash(String v) { return notBlank(v) ? v.trim() : "--:--"; }
        private static String safe(String v) { return v == null ? "￿" : v; }

        @Override public int getCount() { return rows.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) {
            String id = position < rows.size() ? rows.get(position).shift.getShiftId() : null;
            return id != null ? id.hashCode() : position;
        }
        @Override public boolean hasStableIds() { return true; }
        @Override public void onDestroy() { rows.clear(); }
    }
}
