package com.example.attendanceapplication.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.example.attendanceapplication.activities.ScanAttendanceActivity;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.activities.ShiftAttendanceListActivity;
import com.example.attendanceapplication.activities.ShiftDetailActivity;
import com.example.attendanceapplication.activities.SplashActivity;
import com.example.attendanceapplication.activities.StudentMainActivity;
import com.example.attendanceapplication.activities.TeacherMainActivity;
import com.example.attendanceapplication.models.Shift;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Activity trung gian không giao diện: nhận cú bấm từ card/nút trên widget rồi mở
 * đúng màn hình, đặt màn hình chính của vai trò bên dưới để nút Back quay về app.
 */
public class WidgetActionActivity extends Activity {

    static final String EXTRA_ACTION = "widgetAction";
    static final String EXTRA_TEACHER = "widgetTeacher";
    private static final String EXTRA_SHIFT_ID = "widgetShiftId";
    private static final String EXTRA_CLASS_ID = "widgetClassId";
    private static final String EXTRA_CLASS_NAME = "widgetClassName";
    private static final String EXTRA_TITLE = "widgetTitle";
    private static final String EXTRA_DATE = "widgetDate";
    private static final String EXTRA_DAY_OF_WEEK = "widgetDayOfWeek";
    private static final String EXTRA_START_AT = "widgetStartAt";
    private static final String EXTRA_END_AT = "widgetEndAt";
    private static final String EXTRA_ROOM = "widgetRoom";
    private static final String EXTRA_CONTENT = "widgetContent";

    static void putShift(Intent i, Shift s) {
        i.putExtra(EXTRA_SHIFT_ID, s.getShiftId());
        i.putExtra(EXTRA_CLASS_ID, s.getClassId());
        i.putExtra(EXTRA_CLASS_NAME, s.getClassName());
        i.putExtra(EXTRA_TITLE, s.getTitle());
        i.putExtra(EXTRA_DATE, s.getDate());
        i.putExtra(EXTRA_DAY_OF_WEEK, s.getDayOfWeek());
        i.putExtra(EXTRA_START_AT, s.getStartAt());
        i.putExtra(EXTRA_END_AT, s.getEndAt());
        i.putExtra(EXTRA_ROOM, s.getRoom());
        i.putExtra(EXTRA_CONTENT, s.getContent());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        route();
        finish();
    }

    private void route() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openSplash();
            return;
        }
        Intent in = getIntent();
        boolean teacher = in.getBooleanExtra(EXTRA_TEACHER, false);
        ShiftWidgetState.Action action;
        try {
            action = ShiftWidgetState.Action.valueOf(in.getStringExtra(EXTRA_ACTION));
        } catch (Exception e) {
            action = ShiftWidgetState.Action.OPEN_APP;
        }
        Shift s = readShift(in);

        Intent target;
        switch (action) {
            case OPEN_SESSION:
                target = new Intent(this, SessionManagementActivity.class);
                target.putExtra(SessionManagementActivity.EXTRA_SHIFT_ID, s.getShiftId());
                target.putExtra(SessionManagementActivity.EXTRA_CLASS_ID, s.getClassId());
                target.putExtra(SessionManagementActivity.EXTRA_CLASS_NAME, s.getClassName());
                SessionManagementActivity.putShiftExtras(target, s);
                break;
            case VIEW_RESULT:
                target = new Intent(this, ShiftAttendanceListActivity.class);
                target.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_ID, s.getShiftId());
                target.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_ID, s.getClassId());
                target.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_NAME, s.getClassName());
                target.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TITLE,
                        s.getTitle() != null ? s.getTitle() : s.getClassName());
                target.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TIME,
                        s.getDayOfWeekDisplay() + "  " + s.getStartAt() + " - " + s.getEndAt());
                target.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_CONTENT, s.getContent());
                break;
            case SCAN:
                target = new Intent(this, ScanAttendanceActivity.class);
                break;
            case SHIFT_DETAIL:
                target = new Intent(this, ShiftDetailActivity.class);
                target.putExtra(ShiftDetailActivity.EXTRA_SHIFT_ID, s.getShiftId());
                target.putExtra(ShiftDetailActivity.EXTRA_CLASS_ID, s.getClassId());
                target.putExtra(ShiftDetailActivity.EXTRA_CLASS_NAME, s.getClassName());
                break;
            default:
                openSplash();
                return;
        }

        Intent main = new Intent(this, teacher ? TeacherMainActivity.class : StudentMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivities(new Intent[]{main, target});
    }

    private void openSplash() {
        startActivity(new Intent(this, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private static Shift readShift(Intent in) {
        Shift s = new Shift();
        s.setShiftId(in.getStringExtra(EXTRA_SHIFT_ID));
        s.setClassId(in.getStringExtra(EXTRA_CLASS_ID));
        s.setClassName(in.getStringExtra(EXTRA_CLASS_NAME));
        s.setTitle(in.getStringExtra(EXTRA_TITLE));
        s.setDate(in.getStringExtra(EXTRA_DATE));
        s.setDayOfWeek(in.getIntExtra(EXTRA_DAY_OF_WEEK, 0));
        s.setStartAt(in.getStringExtra(EXTRA_START_AT));
        s.setEndAt(in.getStringExtra(EXTRA_END_AT));
        s.setRoom(in.getStringExtra(EXTRA_ROOM));
        s.setContent(in.getStringExtra(EXTRA_CONTENT));
        return s;
    }
}
