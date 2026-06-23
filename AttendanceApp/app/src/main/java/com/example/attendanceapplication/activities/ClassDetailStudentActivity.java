package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class ClassDetailStudentActivity extends AppCompatActivity {

    private String classId, className;
    private TextView tvClassName, tvSchedule, tvAttendanceRate, tvTeacher, tvRoom;
    private TextView tvStatPast, tvStatPresent, tvStatAbsent;
    private RecyclerView rvShifts;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    // Simple shift adapter for student
    private StudentShiftAdapter adapter;
    private List<Shift> shiftList = new ArrayList<>();
    // Latest realtime shifts, kept so onResume can refresh attendance status
    // without re-subscribing the LiveData (which would leak snapshot listeners).
    private List<Shift> latestShifts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_detail_student);

        classId   = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
            getSupportActionBar().setTitle(className);
        }

        tvClassName      = findViewById(R.id.tv_class_name);
        tvSchedule       = findViewById(R.id.tv_schedule);
        tvAttendanceRate = findViewById(R.id.tv_attendance_rate);
        tvTeacher        = findViewById(R.id.tv_teacher);
        tvRoom           = findViewById(R.id.tv_room);
        tvStatPast       = findViewById(R.id.tv_stat_past);
        tvStatPresent    = findViewById(R.id.tv_stat_present);
        tvStatAbsent     = findViewById(R.id.tv_stat_absent);
        rvShifts         = findViewById(R.id.rv_shifts);

        if (className != null) tvClassName.setText(className);

        adapter = new StudentShiftAdapter(shiftList, shift -> {
            Intent i = new Intent(this, ShiftDetailActivity.class);
            i.putExtra(ShiftDetailActivity.EXTRA_SHIFT_ID, shift.getShiftId());
            i.putExtra(ShiftDetailActivity.EXTRA_CLASS_ID, classId);
            i.putExtra(ShiftDetailActivity.EXTRA_CLASS_NAME, className);
            startActivity(i);
        });
        rvShifts.setLayoutManager(new LinearLayoutManager(this));
        rvShifts.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        // Load class info
        repo.getClassById(classId,
                cls -> runOnUiThread(() -> {
                    tvClassName.setText(cls.getClassName());
                    tvSchedule.setText(cls.getScheduleDisplay() + "  " +
                            cls.getStartAt() + "-" + cls.getEndAt());
                    tvRoom.setText(cls.getRoom() != null && !cls.getRoom().isEmpty()
                            ? cls.getRoom() : "Chưa có phòng");
                    // Tên giáo viên: ưu tiên dữ liệu sẵn có, nếu thiếu thì lấy từ users.
                    if (cls.getTeacherName() != null && !cls.getTeacherName().isEmpty()) {
                        tvTeacher.setText(cls.getTeacherName());
                    } else if (cls.getTeacherId() != null && !cls.getTeacherId().isEmpty()) {
                        repo.getUserProfile(cls.getTeacherId(),
                                user -> runOnUiThread(() -> tvTeacher.setText(
                                        user != null && user.getName() != null
                                                ? user.getName() : "Chưa rõ")),
                                err -> {});
                    } else {
                        tvTeacher.setText("Chưa rõ");
                    }
                }),
                e -> {}
        );

        // Load shifts (realtime). Attendance status is refreshed on top of them.
        repo.getClassShifts(classId).observe(this, shifts -> {
            // No shifts in Firestore → show mock data so the screen isn't empty
            if (shifts == null || shifts.isEmpty()) {
                latestShifts = null;
                runOnUiThread(() -> renderShifts(buildMockShifts()));
                return;
            }
            latestShifts = shifts;
            applyAttendanceStatus(shifts);
        });
    }

    /**
     * Re-fetches the student's attendance history (one-shot) and marks each shift,
     * then renders. Called from the realtime shift observer and from onResume,
     * so the status updates immediately after the student checks in and returns.
     */
    private void applyAttendanceStatus(List<Shift> shifts) {
        String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentAttendanceHistory(studentId, classId, attendanceList -> {
            java.util.HashMap<String, String> attMap = new java.util.HashMap<>();
            for (Attendance a : attendanceList) {
                attMap.put(a.getShiftId(), a.getStatus());
            }
            for (Shift s : shifts) {
                String attStatus = attMap.get(s.getShiftId());
                s.setAttendanceStatus(attStatus != null ? attStatus : "not_checked");
            }
            runOnUiThread(() -> renderShifts(shifts));
        }, e -> runOnUiThread(() -> renderShifts(shifts)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (latestShifts != null) applyAttendanceStatus(latestShifts);
    }

    /**
     * Render the shift list and recompute the stat cards.
     * A shift counts as "past" once it is completed or has been attended;
     * past = attended + absent.
     */
    private void renderShifts(List<Shift> shifts) {
        shiftList.clear();
        shiftList.addAll(shifts);

        int attended = 0, absent = 0;
        for (Shift s : shiftList) {
            String st = s.getAttendanceStatus();
            boolean isPresent = "present".equals(st) || "late".equals(st);
            if (isPresent) {
                attended++;
            } else if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
                absent++;
            }
        }
        final int past = attended + absent;
        final int rate = past > 0 ? (attended * 100 / past) : 0;

        adapter.notifyDataSetChanged();

        tvStatPast.setText(String.valueOf(past));
        tvStatPresent.setText(String.valueOf(attended));
        tvStatAbsent.setText(String.valueOf(absent));

        tvAttendanceRate.setText(String.format(
                "Điểm danh: %d/%d buổi (%d%%)", attended, past, rate));
        if (rate >= 80) tvAttendanceRate.setTextColor(getColor(R.color.accent_green));
        else if (rate >= 60) tvAttendanceRate.setTextColor(getColor(R.color.warning_yellow));
        else tvAttendanceRate.setTextColor(getColor(R.color.error_red));
    }

    /**
     * Mock shift list used when this class has no shifts in Firestore yet,
     * so the screen still demonstrates attended / absent / upcoming states.
     */
    private List<Shift> buildMockShifts() {
        if (tvSchedule.getText().toString().trim().isEmpty()
                || "T2+T4  18:00-20:00".equals(tvSchedule.getText().toString())) {
            tvSchedule.setText("T4  16:17-17:25  (dữ liệu mẫu)");
        }

        // statusOfShift, attendanceStatus, attendanceOpened, daysFromToday
        Object[][] specs = {
                {Shift.STATUS_COMPLETED, "present",     false, -21},
                {Shift.STATUS_COMPLETED, "present",     false, -14},
                {Shift.STATUS_COMPLETED, "not_checked", false, -7},
                {Shift.STATUS_COMPLETED, "late",        false, -3},
                {Shift.STATUS_ONGOING,   "not_checked", true,   0},
                {Shift.STATUS_UPCOMING,  "not_checked", false,  4},
                {Shift.STATUS_UPCOMING,  "not_checked", false,  11},
        };

        java.text.SimpleDateFormat df =
                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        List<Shift> mock = new ArrayList<>();
        for (int i = 0; i < specs.length; i++) {
            Object[] spec = specs[i];
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, (int) spec[3]);

            Shift s = new Shift();
            s.setShiftId("mock_" + i);
            s.setClassId(classId);
            s.setClassName(className);
            s.setDate(df.format(cal.getTime()));
            s.setDayOfWeek(com.example.attendanceapplication.utils.AttendanceUtils
                    .getDayOfWeekVN(cal.get(java.util.Calendar.DAY_OF_WEEK)));
            s.setStartAt("16:17");
            s.setEndAt("17:25");
            s.setStatus((String) spec[0]);
            s.setAttendanceStatus((String) spec[1]);
            s.setAttendanceOpened((boolean) spec[2]);
            mock.add(s);
        }
        return mock;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ── Inner adapter ────────────────────────────────────────────────────────
    static class StudentShiftAdapter
            extends RecyclerView.Adapter<StudentShiftAdapter.VH> {

        interface OnShiftClick { void onClick(Shift s); }

        private final List<Shift> list;
        private final OnShiftClick listener;

        StudentShiftAdapter(List<Shift> list, OnShiftClick listener) {
            this.list = list;
            this.listener = listener;
        }

        @androidx.annotation.NonNull
        @Override
        public VH onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent,
                                     int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shift_student, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull VH h, int pos) {
            Shift s = list.get(pos);
            h.tvDate.setText(shortDate(s.getDate()));
            h.tvDay.setText(s.getDayOfWeekDisplay());
            h.tvTime.setText(s.getStartAt() + " - " + s.getEndAt());

            android.content.Context ctx = h.itemView.getContext();
            String attStatus = s.getAttendanceStatus();
            int iconRes, colorRes;
            String label;
            if ("present".equals(attStatus) || "late".equals(attStatus)) {
                iconRes = R.drawable.ic_check_circle; colorRes = R.color.accent_green;
                label = "Đã điểm danh";
            } else if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
                iconRes = R.drawable.ic_cancel; colorRes = R.color.error_red;
                label = "Vắng mặt";
            } else if (s.isAttendanceOpened()) {
                iconRes = R.drawable.ic_qr; colorRes = R.color.primary_blue;
                label = "Điểm danh ngay!";
            } else {
                iconRes = R.drawable.ic_clock; colorRes = R.color.text_secondary;
                label = "Chưa mở";
            }
            int color = androidx.core.content.ContextCompat.getColor(ctx, colorRes);
            h.ivAttIcon.setImageResource(iconRes);
            h.ivAttIcon.setColorFilter(color);
            h.tvAttLabel.setText(label);
            h.tvAttLabel.setTextColor(color);

            h.itemView.setOnClickListener(v -> listener.onClick(s));
        }

        @Override public int getItemCount() { return list.size(); }

        // "2026-05-31" -> "31/05"; falls back to the raw value if unparseable.
        private static String shortDate(String date) {
            if (date == null) return "";
            String[] p = date.split("-");
            if (p.length == 3) return p[2] + "/" + p[1];
            return date;
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvDay, tvTime, tvAttLabel;
            android.widget.ImageView ivAttIcon;
            VH(@androidx.annotation.NonNull android.view.View v) {
                super(v);
                tvDate     = v.findViewById(R.id.tv_date);
                tvDay      = v.findViewById(R.id.tv_day);
                tvTime     = v.findViewById(R.id.tv_time);
                ivAttIcon  = v.findViewById(R.id.iv_att_icon);
                tvAttLabel = v.findViewById(R.id.tv_att_label);
            }
        }
    }
}
