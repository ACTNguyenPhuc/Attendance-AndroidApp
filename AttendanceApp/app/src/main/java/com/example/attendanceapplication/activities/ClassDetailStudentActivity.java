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
    private TextView tvClassName, tvSchedule, tvAttendanceRate;
    private RecyclerView rvShifts;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    // Simple shift adapter for student
    private StudentShiftAdapter adapter;
    private List<Shift> shiftList = new ArrayList<>();

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
            getSupportActionBar().setTitle(className);
        }

        tvClassName      = findViewById(R.id.tv_class_name);
        tvSchedule       = findViewById(R.id.tv_schedule);
        tvAttendanceRate = findViewById(R.id.tv_attendance_rate);
        rvShifts         = findViewById(R.id.rv_shifts);

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
                }),
                e -> {}
        );

        // Load shifts with attendance status
        String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getClassShifts(classId).observe(this, shifts -> {
            shiftList.clear();

            // Load student attendance history for this class to mark each shift
            repo.getStudentAttendanceHistory(studentId, classId, attendanceList -> {
                // Build a map: shiftId -> status
                java.util.HashMap<String, String> attMap = new java.util.HashMap<>();
                for (Attendance a : attendanceList) {
                    attMap.put(a.getShiftId(), a.getStatus());
                }

                for (Shift s : shifts) {
                    String attStatus = attMap.get(s.getShiftId());
                    if (attStatus != null) {
                        s.setAttendanceStatus(attStatus);
                    } else {
                        s.setAttendanceStatus("not_checked");
                    }
                    shiftList.add(s);
                }

                // Calculate rate (capture final values for UI lambda)
                final int total = shiftList.size();
                int presentCount = 0;
                for (Shift s : shiftList) {
                    if ("present".equals(s.getAttendanceStatus()) ||
                            "late".equals(s.getAttendanceStatus())) {
                        presentCount++;
                    }
                }
                final int present = presentCount;
                final int rate = total > 0 ? (present * 100 / total) : 0;

                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    tvAttendanceRate.setText(String.format(
                            "Điểm danh: %d/%d buổi (%d%%)", present, total, rate));
                    // Color rate
                    if (rate >= 80) tvAttendanceRate.setTextColor(getColor(R.color.accent_green));
                    else if (rate >= 60) tvAttendanceRate.setTextColor(getColor(R.color.warning_yellow));
                    else tvAttendanceRate.setTextColor(getColor(R.color.error_red));
                });
            }, e -> runOnUiThread(() -> adapter.notifyDataSetChanged()));
        });
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
            h.tvDate.setText(s.getDate());
            h.tvDay.setText(s.getDayOfWeekDisplay());
            h.tvTime.setText(s.getStartAt() + " - " + s.getEndAt());

            String attStatus = s.getAttendanceStatus();
            if ("present".equals(attStatus) || "late".equals(attStatus)) {
                h.tvAttIcon.setText("✅");
                h.tvAttLabel.setText("Đã điểm danh");
                h.tvAttLabel.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                h.itemView.getContext(), R.color.accent_green));
            } else if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
                h.tvAttIcon.setText("❌");
                h.tvAttLabel.setText("Vắng mặt");
                h.tvAttLabel.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                h.itemView.getContext(), R.color.error_red));
            } else if (s.isAttendanceOpened()) {
                h.tvAttIcon.setText("📷");
                h.tvAttLabel.setText("Điểm danh ngay!");
                h.tvAttLabel.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                h.itemView.getContext(), R.color.primary_blue));
            } else {
                h.tvAttIcon.setText("🕐");
                h.tvAttLabel.setText("Chưa mở");
                h.tvAttLabel.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                h.itemView.getContext(), R.color.text_secondary));
            }

            h.itemView.setOnClickListener(v -> listener.onClick(s));
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvDay, tvTime, tvAttIcon, tvAttLabel;
            VH(@androidx.annotation.NonNull android.view.View v) {
                super(v);
                tvDate     = v.findViewById(R.id.tv_date);
                tvDay      = v.findViewById(R.id.tv_day);
                tvTime     = v.findViewById(R.id.tv_time);
                tvAttIcon  = v.findViewById(R.id.tv_att_icon);
                tvAttLabel = v.findViewById(R.id.tv_att_label);
            }
        }
    }
}
