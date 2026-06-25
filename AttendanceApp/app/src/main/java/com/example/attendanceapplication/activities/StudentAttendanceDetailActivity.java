package com.example.attendanceapplication.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chi tiết điểm danh của một sinh viên trong một môn học.
 * Liệt kê mọi buổi học ĐÃ KẾT THÚC (completed) kèm trạng thái:
 *  - Có mặt: thêm tag "Đúng giờ" (present) hoặc "Muộn" (late).
 *  - Vắng: buổi đã qua nhưng sinh viên không có bản ghi điểm danh.
 */
public class StudentAttendanceDetailActivity extends AppCompatActivity {

    public static final String EXTRA_STUDENT_ID = "studentId";
    public static final String EXTRA_STUDENT_NAME = "studentName";
    public static final String EXTRA_STUDENT_CODE = "studentCode";
    public static final String EXTRA_CLASS_ID = "classId";
    public static final String EXTRA_CLASS_NAME = "className";

    private TextView tvAvatar, tvName, tvCode, tvRate, tvTotal, tvPresent, tvLate, tvAbsent, tvEmpty;
    private RecyclerView rvRecords;

    private RecordAdapter adapter;
    private final List<Record> records = new ArrayList<>();

    private final List<Shift> completedShifts = new ArrayList<>();

    private String studentId, studentName, studentCode, classId, className;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance_detail);

        studentId   = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        studentName = getIntent().getStringExtra(EXTRA_STUDENT_NAME);
        studentCode = getIntent().getStringExtra(EXTRA_STUDENT_CODE);
        classId     = getIntent().getStringExtra(EXTRA_CLASS_ID);
        className   = getIntent().getStringExtra(EXTRA_CLASS_NAME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết điểm danh");
            if (className != null && !className.isEmpty()) {
                getSupportActionBar().setSubtitle(className);
            }
        }

        tvAvatar  = findViewById(R.id.tv_avatar);
        tvName    = findViewById(R.id.tv_name);
        tvCode    = findViewById(R.id.tv_code);
        tvRate    = findViewById(R.id.tv_rate);
        tvTotal   = findViewById(R.id.tv_total);
        tvPresent = findViewById(R.id.tv_present);
        tvLate    = findViewById(R.id.tv_late);
        tvAbsent  = findViewById(R.id.tv_absent);
        tvEmpty   = findViewById(R.id.tv_empty);
        rvRecords = findViewById(R.id.rv_records);

        tvName.setText(studentName != null && !studentName.isEmpty() ? studentName : "(Không tên)");
        tvCode.setText(studentCode != null ? studentCode : "");
        if (studentName != null && !studentName.isEmpty()) {
            tvAvatar.setText(String.valueOf(studentName.charAt(0)).toUpperCase(Locale.getDefault()));
        }

        adapter = new RecordAdapter(records);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);

        loadData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        if (classId == null || studentId == null) {
            showEmpty(true);
            return;
        }
        // Quan sát realtime danh sách buổi học của lớp; mỗi lần thay đổi sẽ lấy lại
        // bản ghi điểm danh của sinh viên rồi dựng lại danh sách (đồng bộ trạng thái).
        repo.getClassShifts(classId).observe(this, shifts -> {
            completedShifts.clear();
            for (Shift s : shifts) {
                if (Shift.STATUS_COMPLETED.equals(s.getStatus())) completedShifts.add(s);
            }
            repo.getStudentAttendanceHistory(studentId, classId,
                    atts -> runOnUiThread(() -> rebuild(atts)),
                    e -> runOnUiThread(() -> {
                        Toast.makeText(this, "Lỗi tải điểm danh: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        rebuild(new ArrayList<>());
                    }));
        });
    }

    private void rebuild(List<Attendance> attendances) {
        Map<String, Attendance> byShift = new HashMap<>();
        if (attendances != null) {
            for (Attendance a : attendances) {
                if (a.getShiftId() != null) byShift.put(a.getShiftId(), a);
            }
        }

        // Mới nhất lên đầu (chuỗi yyyy-MM-dd và HH:mm so sánh được theo thời gian).
        List<Shift> sorted = new ArrayList<>(completedShifts);
        sorted.sort((a, b) -> {
            String da = a.getDate() == null ? "" : a.getDate();
            String dbb = b.getDate() == null ? "" : b.getDate();
            int c = dbb.compareTo(da);
            if (c != 0) return c;
            String sa = a.getStartAt() == null ? "" : a.getStartAt();
            String sb = b.getStartAt() == null ? "" : b.getStartAt();
            return sb.compareTo(sa);
        });

        records.clear();
        int present = 0, late = 0, absent = 0;
        for (Shift s : sorted) {
            Record r = new Record();
            r.label = (s.getContent() != null && !s.getContent().isEmpty()) ? s.getContent()
                    : (s.getTitle() != null && !s.getTitle().isEmpty() ? s.getTitle() : "Buổi học");
            r.dateText = buildDateLine(s);

            Attendance att = byShift.get(s.getShiftId());
            if (att != null) {
                r.present = true;
                r.late = Attendance.STATUS_LATE.equals(att.getStatus());
                r.checkinText = att.getCheckinTime() != null
                        ? formatTime(att.getCheckinTime().toDate().getTime()) : null;
                if (r.late) late++; else present++;
            } else {
                r.present = false;
                absent++;
            }
            records.add(r);
        }
        adapter.notifyDataSetChanged();

        int total = sorted.size();
        int attended = present + late;             // tổng số buổi có mặt (gồm cả muộn)
        int rate = total > 0 ? Math.round(attended * 100f / total) : 0;
        tvTotal.setText(String.valueOf(total));
        tvPresent.setText(String.valueOf(attended));
        tvLate.setText(String.valueOf(late));
        tvAbsent.setText(String.valueOf(absent));
        tvRate.setText(rate + "%");

        showEmpty(total == 0);
    }

    private void showEmpty(boolean empty) {
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvRecords.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** "T4 • 8/5/2026 • 07:00-09:00" */
    private String buildDateLine(Shift s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.getDayOfWeekDisplay());
        sb.append(" • ").append(displayDate(s.getDate()));
        if (s.getStartAt() != null && !s.getStartAt().isEmpty()) {
            sb.append(" • ").append(s.getStartAt());
            if (s.getEndAt() != null && !s.getEndAt().isEmpty()) sb.append("-").append(s.getEndAt());
        }
        return sb.toString();
    }

    /** "yyyy-MM-dd" -> "d/M/yyyy". */
    private String displayDate(String date) {
        if (date == null || date.isEmpty()) return "";
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateKeyFormat.parse(date));
            return cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1)
                    + "/" + cal.get(Calendar.YEAR);
        } catch (Exception e) {
            return date;
        }
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date(millis));
    }

    /** Dữ liệu một dòng buổi học đã dựng sẵn. */
    static class Record {
        String label;
        String dateText;
        String checkinText;   // null nếu vắng
        boolean present;
        boolean late;
    }

    static class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {
        private final List<Record> items;

        RecordAdapter(List<Record> items) { this.items = items; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Record r = items.get(position);
            Context ctx = h.itemView.getContext();
            h.tvLabel.setText(r.label);
            h.tvDateTime.setText(r.dateText);

            // Icon + tag theo trạng thái. Với bản ghi có mặt chỉ hiện tag "Đúng giờ"/"Muộn".
            int iconRes, colorRes, badgeBg;
            String badgeText;
            if (r.present) {
                if (r.late) {
                    iconRes = R.drawable.ic_clock;
                    colorRes = R.color.warning_yellow;
                    badgeBg = R.drawable.bg_badge_orange;
                    badgeText = "MUỘN";
                } else {
                    iconRes = R.drawable.ic_check_circle;
                    colorRes = R.color.accent_green;
                    badgeBg = R.drawable.bg_badge_green;
                    badgeText = "ĐÚNG GIỜ";
                }
                if (r.checkinText != null) {
                    h.tvCheckin.setText("Điểm danh: " + r.checkinText);
                    h.tvCheckin.setVisibility(View.VISIBLE);
                } else {
                    h.tvCheckin.setVisibility(View.GONE);
                }
            } else {
                iconRes = R.drawable.ic_cancel;
                colorRes = R.color.error_red;
                badgeBg = R.drawable.bg_badge_red;
                badgeText = "VẮNG";
                h.tvCheckin.setVisibility(View.GONE);
            }

            h.ivStatusIcon.setImageResource(iconRes);
            h.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, colorRes));
            h.tvStatusBadge.setText(badgeText);
            h.tvStatusBadge.setBackgroundResource(badgeBg);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivStatusIcon;
            TextView tvLabel, tvDateTime, tvCheckin, tvStatusBadge;
            VH(@NonNull View v) {
                super(v);
                ivStatusIcon = v.findViewById(R.id.iv_status_icon);
                tvLabel      = v.findViewById(R.id.tv_label);
                tvDateTime   = v.findViewById(R.id.tv_datetime);
                tvCheckin    = v.findViewById(R.id.tv_checkin);
                tvStatusBadge = v.findViewById(R.id.tv_status_badge);
            }
        }
    }
}
