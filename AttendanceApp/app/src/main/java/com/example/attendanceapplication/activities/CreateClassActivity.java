package com.example.attendanceapplication.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.DaySchedule;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.*;

public class CreateClassActivity extends AppCompatActivity {

    public static final String EXTRA_CREATED_CLASS_ID = "createdClassId";

    private TextInputEditText etClassName, etClassId, etDescription, etRoom;
    private TextView tvStartDate, tvEndDate, tvShiftPreview;
    private ChipGroup chipGroupSchedule;
    private LinearLayout llDayTimes;
    private Button btnCreate;
    private View loadingOverlay;

    private String startDate = "", endDate = "";
    // Giờ học theo từng thứ (2=T2 … 8=CN). LinkedHashMap để giữ thứ tự ngày được chọn.
    private final Map<Integer, String> startByDay = new LinkedHashMap<>();
    private final Map<Integer, String> endByDay = new LinkedHashMap<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_class);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tạo lớp học mới");
        }
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon.setTint(ContextCompat.getColor(this, R.color.white));
        }
        initViews();
        setupListeners();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        etClassName    = findViewById(R.id.et_class_name);
        etClassId      = findViewById(R.id.et_class_id);
        etDescription  = findViewById(R.id.et_description);
        etRoom         = findViewById(R.id.et_room);
        tvStartDate    = findViewById(R.id.tv_start_date);
        tvEndDate      = findViewById(R.id.tv_end_date);
        tvShiftPreview = findViewById(R.id.tv_shift_preview);
        chipGroupSchedule = findViewById(R.id.chip_group_schedule);
        llDayTimes     = findViewById(R.id.ll_day_times);
        btnCreate      = findViewById(R.id.btn_create);
        loadingOverlay = findViewById(R.id.loading_overlay);
    }

    private void setupListeners() {
        tvStartDate.setOnClickListener(v -> showDatePicker(true));
        tvEndDate.setOnClickListener(v -> showDatePicker(false));
        chipGroupSchedule.setOnCheckedStateChangeListener((g, ids) -> {
            rebuildDayTimeRows();
            updateShiftPreview();
        });
        btnCreate.setOnClickListener(v -> createClass());
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(y, m, d);
            String dateStr = sdf.format(selected.getTime());
            if (isStart) {
                startDate = dateStr;
                tvStartDate.setText(dateStr);
            } else {
                endDate = dateStr;
                tvEndDate.setText(dateStr);
            }
            updateShiftPreview();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    /**
     * Dựng lại danh sách dòng nhập giờ theo các ngày đang được chọn. Giờ đã nhập
     * của những ngày vẫn được chọn được giữ nguyên; ngày bỏ chọn sẽ bị xóa giờ.
     */
    private void rebuildDayTimeRows() {
        List<Integer> selected = getSelectedSchedule();
        startByDay.keySet().retainAll(selected);
        endByDay.keySet().retainAll(selected);

        llDayTimes.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int day : selected) {
            View row = inflater.inflate(R.layout.item_day_time_picker, llDayTimes, false);
            TextView tvLabel = row.findViewById(R.id.tv_day_label);
            TextView tvStart = row.findViewById(R.id.tv_start_time);
            TextView tvEnd   = row.findViewById(R.id.tv_end_time);

            tvLabel.setText(dayLabel(day));
            if (startByDay.containsKey(day)) tvStart.setText(startByDay.get(day));
            if (endByDay.containsKey(day))   tvEnd.setText(endByDay.get(day));

            tvStart.setOnClickListener(v -> showDayTimePicker(day, true, tvStart));
            tvEnd.setOnClickListener(v -> showDayTimePicker(day, false, tvEnd));

            llDayTimes.addView(row);
        }
    }

    private void showDayTimePicker(int day, boolean isStart, TextView target) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY), minute = cal.get(Calendar.MINUTE);
        String existing = isStart ? startByDay.get(day) : endByDay.get(day);
        if (existing != null) {
            String[] p = existing.split(":");
            try {
                hour = Integer.parseInt(p[0]);
                minute = Integer.parseInt(p[1]);
            } catch (Exception ignored) {}
        }
        TimePickerDialog dialog = new TimePickerDialog(this, (view, h, m) -> {
            String time = String.format(Locale.US, "%02d:%02d", h, m);
            if (isStart) startByDay.put(day, time); else endByDay.put(day, time);
            target.setText(time);
        }, hour, minute, true);
        dialog.show();
    }

    private List<Integer> getSelectedSchedule() {
        List<Integer> schedule = new ArrayList<>();
        int[] dayValues = {2, 3, 4, 5, 6, 7, 8}; // T2..T8 (CN=8)
        int[] chipIds = {
            R.id.chip_mon, R.id.chip_tue, R.id.chip_wed,
            R.id.chip_thu, R.id.chip_fri, R.id.chip_sat, R.id.chip_sun
        };
        for (int i = 0; i < chipIds.length; i++) {
            Chip chip = findViewById(chipIds[i]);
            if (chip != null && chip.isChecked()) schedule.add(dayValues[i]);
        }
        return schedule;
    }

    private void updateShiftPreview() {
        if (startDate.isEmpty() || endDate.isEmpty()) return;
        List<Integer> schedule = getSelectedSchedule();
        if (schedule.isEmpty()) { tvShiftPreview.setText(""); return; }

        try {
            Date start = sdf.parse(startDate);
            Date end   = sdf.parse(endDate);
            if (start == null || end == null) return;

            int count = 0;
            Calendar cal = Calendar.getInstance();
            cal.setTime(start);
            while (!cal.getTime().after(end)) {
                int dow = cal.get(Calendar.DAY_OF_WEEK);
                int vnDow = dow == 1 ? 8 : dow; // Sun=8
                if (schedule.contains(vnDow)) count++;
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            String schedStr = getScheduleString(schedule);
            tvShiftPreview.setText(String.format(
                    "Hệ thống sẽ tạo %d buổi học (từ %s → %s, mỗi tuần %s)",
                    count, startDate, endDate, schedStr));
        } catch (Exception e) {
            tvShiftPreview.setText("");
        }
    }

    private String getScheduleString(List<Integer> schedule) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < schedule.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(dayLabel(schedule.get(i)));
        }
        return sb.toString();
    }

    private String dayLabel(int day) {
        String[] names = {"", "CN", "T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        return (day >= 0 && day < names.length) ? names[day] : "T" + day;
    }

    private void createClass() {
        String className = etClassName.getText().toString().trim();
        String classId   = etClassId.getText().toString().trim();

        if (className.isEmpty() || classId.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin bắt buộc", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isEndDateAfterStartDate()) {
            Toast.makeText(this, "Ngày kết thúc phải sau ngày bắt đầu", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Integer> schedule = getSelectedSchedule();
        if (schedule.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ngày học trong tuần", Toast.LENGTH_SHORT).show();
            return;
        }
        // Mỗi ngày phải có đủ giờ bắt đầu/kết thúc và giờ kết thúc sau giờ bắt đầu.
        for (int day : schedule) {
            String s = startByDay.get(day), e = endByDay.get(day);
            if (s == null || s.isEmpty() || e == null || e.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập giờ học cho " + dayLabel(day), Toast.LENGTH_SHORT).show();
                return;
            }
            if (toMinutes(e) <= toMinutes(s)) {
                Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu (" + dayLabel(day) + ")", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        List<DaySchedule> daySchedules = new ArrayList<>();
        for (int day : schedule) {
            daySchedules.add(new DaySchedule(day, startByDay.get(day), endByDay.get(day)));
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        loadingOverlay.setVisibility(View.VISIBLE);
        btnCreate.setEnabled(false);

        // Fetch teacher name first; nếu lỗi vẫn tạo lớp nhưng thiếu tên giáo viên.
        repo.getUserProfile(uid,
                teacher -> submitClass(uid, teacher.getName(), schedule, daySchedules),
                e -> submitClass(uid, null, schedule, daySchedules)
        );
    }

    private void submitClass(String uid, String teacherName,
                             List<Integer> schedule, List<DaySchedule> daySchedules) {
        ClassModel classModel = new ClassModel();
        classModel.setClassId(etClassId.getText().toString().trim());
        classModel.setClassName(etClassName.getText().toString().trim());
        classModel.setTeacherId(uid);
        if (teacherName != null) classModel.setTeacherName(teacherName);
        classModel.setStartDate(startDate);
        classModel.setEndDate(endDate);
        classModel.setSchedule(schedule);
        classModel.setDaySchedules(daySchedules);
        // Giờ chung (legacy/fallback): lấy theo ngày học đầu tiên.
        DaySchedule first = daySchedules.get(0);
        classModel.setStartAt(first.getStartAt());
        classModel.setEndAt(first.getEndAt());
        classModel.setRoom(etRoom.getText().toString().trim());
        classModel.setDescription(etDescription.getText().toString().trim());

        repo.createClass(classModel,
                id -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Tạo lớp học thành công!", Toast.LENGTH_SHORT).show();
                    finishWithCreatedClass(id);
                },
                err -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnCreate.setEnabled(true);
                    Snackbar.make(btnCreate, "Lỗi: " + err.getMessage(), Snackbar.LENGTH_LONG).show();
                }
        );
    }

    /** Ngày kết thúc phải sau ngày bắt đầu (strictly after). */
    private boolean isEndDateAfterStartDate() {
        try {
            Date start = sdf.parse(startDate);
            Date end   = sdf.parse(endDate);
            return start != null && end != null && end.after(start);
        } catch (Exception e) {
            return false;
        }
    }

    private int toMinutes(String time) {
        try {
            String[] p = time.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private void finishWithCreatedClass(String classId) {
        Intent result = new Intent();
        result.putExtra(EXTRA_CREATED_CLASS_ID, classId);
        setResult(RESULT_OK, result);
        finish();
    }
}
