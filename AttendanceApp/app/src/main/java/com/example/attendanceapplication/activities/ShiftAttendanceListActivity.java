package com.example.attendanceapplication.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.RealtimeAttendanceAdapter;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShiftAttendanceListActivity extends AppCompatActivity {

    public static final String EXTRA_SHIFT_ID = "shiftId";
    public static final String EXTRA_CLASS_ID = "classId";
    public static final String EXTRA_CLASS_NAME = "className";
    public static final String EXTRA_SHIFT_TITLE = "shiftTitle";
    public static final String EXTRA_SHIFT_TIME = "shiftTime";

    private TextView tvShiftTitle;
    private TextView tvShiftTime;
    private TextView tvTotalStudents;
    private TextView tvTotalAttended;
    private TextView tvTotalAbsent;
    private TextView tvAttendedEmpty;
    private TextView tvAbsentEmpty;
    private RecyclerView rvAttendance;
    private RecyclerView rvAbsent;

    private final List<Attendance> attendanceList = new ArrayList<>();
    private final List<User> absentList = new ArrayList<>();
    private RealtimeAttendanceAdapter adapter;
    private AbsentStudentAdapter absentAdapter;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    private final List<User> classStudents = new ArrayList<>();
    private String classId;
    private String shiftId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shift_attendance_list);

        shiftId = getIntent().getStringExtra(EXTRA_SHIFT_ID);
        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        String shiftTitle = getIntent().getStringExtra(EXTRA_SHIFT_TITLE);
        String shiftTime = getIntent().getStringExtra(EXTRA_SHIFT_TIME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Điểm danh - " + (className != null ? className : ""));
        }

        tvShiftTitle = findViewById(R.id.tv_shift_title);
        tvShiftTime = findViewById(R.id.tv_shift_time);
        tvTotalStudents = findViewById(R.id.tv_total_students);
        tvTotalAttended = findViewById(R.id.tv_total_attended);
        tvTotalAbsent = findViewById(R.id.tv_total_absent);
        tvAttendedEmpty = findViewById(R.id.tv_attended_empty);
        tvAbsentEmpty = findViewById(R.id.tv_absent_empty);
        rvAttendance = findViewById(R.id.rv_attendance);
        rvAbsent = findViewById(R.id.rv_absent);

        tvShiftTitle.setText(shiftTitle != null ? shiftTitle : "Buổi học");
        tvShiftTime.setText(shiftTime != null ? shiftTime : "");

        adapter = new RealtimeAttendanceAdapter(attendanceList);
        rvAttendance.setLayoutManager(new LinearLayoutManager(this));
        rvAttendance.setAdapter(adapter);

        absentAdapter = new AbsentStudentAdapter(absentList);
        rvAbsent.setLayoutManager(new LinearLayoutManager(this));
        rvAbsent.setAdapter(absentAdapter);

        loadData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        if (shiftId == null || classId == null) {
            setEmpty(true, true);
            return;
        }

        repo.getClassStudents(classId,
                students -> runOnUiThread(() -> {
                    classStudents.clear();
                    classStudents.addAll(students);
                    computeLists();
                }),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, "Lỗi tải sinh viên: " + e.getMessage(), Toast.LENGTH_SHORT).show())
        );

        repo.getShiftAttendances(shiftId,
                list -> runOnUiThread(() -> {
                    attendanceList.clear();
                    attendanceList.addAll(list);
                    adapter.notifyDataSetChanged();
                    computeLists();
                }),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, "Lỗi tải điểm danh: " + e.getMessage(), Toast.LENGTH_SHORT).show())
        );
    }

    private void computeLists() {
        // Bổ sung tên + mã SV cho các bản ghi điểm danh còn thiếu (lưu trước đây
        // chưa có studentName/studentCode), lấy từ danh sách sinh viên của lớp.
        Map<String, User> studentById = new HashMap<>();
        for (User u : classStudents) {
            if (u.getUid() != null) studentById.put(u.getUid(), u);
        }
        for (Attendance a : attendanceList) {
            User u = a.getStudentId() == null ? null : studentById.get(a.getStudentId());
            if (u == null) continue;
            if (a.getStudentName() == null || a.getStudentName().isEmpty()) {
                a.setStudentName(u.getName());
            }
            if (a.getStudentCode() == null || a.getStudentCode().isEmpty()) {
                a.setStudentCode(u.getStudentCode());
            }
        }
        adapter.notifyDataSetChanged();

        int totalStudents = classStudents.size();
        int attended = attendanceList.size();
        int absent = Math.max(0, totalStudents - attended);

        tvTotalStudents.setText(String.valueOf(totalStudents));
        tvTotalAttended.setText(String.valueOf(attended));
        tvTotalAbsent.setText(String.valueOf(absent));

        Set<String> attendedIds = new HashSet<>();
        for (Attendance a : attendanceList) {
            if (a.getStudentId() != null) attendedIds.add(a.getStudentId());
        }

        absentList.clear();
        for (User u : classStudents) {
            if (u.getUid() != null && !attendedIds.contains(u.getUid())) {
                absentList.add(u);
            }
        }
        absentAdapter.notifyDataSetChanged();

        setEmpty(attendanceList.isEmpty(), absentList.isEmpty());
    }

    private void setEmpty(boolean attendedEmpty, boolean absentEmpty) {
        rvAttendance.setVisibility(attendedEmpty ? View.GONE : View.VISIBLE);
        tvAttendedEmpty.setVisibility(attendedEmpty ? View.VISIBLE : View.GONE);
        rvAbsent.setVisibility(absentEmpty ? View.GONE : View.VISIBLE);
        tvAbsentEmpty.setVisibility(absentEmpty ? View.VISIBLE : View.GONE);
    }

    static class AbsentStudentAdapter extends RecyclerView.Adapter<AbsentStudentAdapter.VH> {
        private final List<User> users;

        AbsentStudentAdapter(List<User> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getItemView(parent);
            return new VH(v);
        }

        private View getItemView(ViewGroup parent) {
            return getInflater(parent).inflate(R.layout.item_student_absent, parent, false);
        }

        private LayoutInflater getInflater(ViewGroup parent) {
            return LayoutInflater.from(parent.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            User u = users.get(position);
            holder.tvName.setText(u.getName() != null ? u.getName() : u.getUid());
            holder.tvCode.setText(u.getStudentCode() != null ? u.getStudentCode() : "");
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCode;
            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_student_name);
                tvCode = itemView.findViewById(R.id.tv_student_code);
            }
        }
    }
}
