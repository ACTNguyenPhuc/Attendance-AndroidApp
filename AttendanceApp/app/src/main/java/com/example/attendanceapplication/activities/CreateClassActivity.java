package com.example.attendanceapplication.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;
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
    private TextView tvStartDate, tvEndDate, tvStartTime, tvEndTime, tvShiftPreview;
    private ChipGroup chipGroupSchedule;
    private Button btnCreate;
    private View loadingOverlay;

    private String startDate = "", endDate = "", startAt = "", endAt = "";
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
        tvStartTime    = findViewById(R.id.tv_start_time);
        tvEndTime      = findViewById(R.id.tv_end_time);
        tvShiftPreview = findViewById(R.id.tv_shift_preview);
        chipGroupSchedule = findViewById(R.id.chip_group_schedule);
        btnCreate      = findViewById(R.id.btn_create);
        loadingOverlay = findViewById(R.id.loading_overlay);
    }

    private void setupListeners() {
        tvStartDate.setOnClickListener(v -> showDatePicker(true));
        tvEndDate.setOnClickListener(v -> showDatePicker(false));
        tvStartTime.setOnClickListener(v -> showTimePicker(true));
        tvEndTime.setOnClickListener(v -> showTimePicker(false));
        chipGroupSchedule.setOnCheckedStateChangeListener((g, ids) -> updateShiftPreview());
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

    private void showTimePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(this, (view, h, m) -> {
            String time = String.format(Locale.US, "%02d:%02d", h, m);
            if (isStart) {
                startAt = time;
                tvStartTime.setText(time);
            } else {
                endAt = time;
                tvEndTime.setText(time);
            }
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
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
        String[] names = {"", "CN", "T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < schedule.size(); i++) {
            if (i > 0) sb.append("+");
            int day = schedule.get(i);
            if (day >= 0 && day < names.length) sb.append(names[day]);
        }
        return sb.toString();
    }

    private void createClass() {
        String className = etClassName.getText().toString().trim();
        String classId   = etClassId.getText().toString().trim();

        if (className.isEmpty() || classId.isEmpty() || startDate.isEmpty() ||
                endDate.isEmpty() || startAt.isEmpty() || endAt.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin bắt buộc", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isEndDateAfterStartDate()) {
            Toast.makeText(this, "Ngày kết thúc phải sau ngày bắt đầu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isEndTimeAfterStartTime()) {
            Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Integer> schedule = getSelectedSchedule();
        if (schedule.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ngày học trong tuần", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        loadingOverlay.setVisibility(View.VISIBLE);
        btnCreate.setEnabled(false);

        // Fetch teacher name first
        repo.getUserProfile(uid,
                teacher -> {
                    ClassModel classModel = new ClassModel();
                    classModel.setClassId(classId);
                    classModel.setClassName(className);
                    classModel.setTeacherId(uid);
                    classModel.setTeacherName(teacher.getName());
                    classModel.setStartDate(startDate);
                    classModel.setEndDate(endDate);
                    classModel.setSchedule(schedule);
                    classModel.setStartAt(startAt);
                    classModel.setEndAt(endAt);
                    classModel.setRoom(etRoom.getText().toString().trim());
                    classModel.setDescription(etDescription.getText().toString().trim());

                    repo.createClass(classModel,
                            id -> {
                                loadingOverlay.setVisibility(View.GONE);
                                Toast.makeText(this, "Tạo lớp học thành công!", Toast.LENGTH_SHORT).show();
                                finishWithCreatedClass(id);
                            },
                            e -> {
                                loadingOverlay.setVisibility(View.GONE);
                                btnCreate.setEnabled(true);
                                Snackbar.make(btnCreate, "Lỗi: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                            }
                    );
                },
                e -> {
                    // Fallback: create without teacher name
                    ClassModel classModel = new ClassModel();
                    classModel.setClassId(classId);
                    classModel.setClassName(className);
                    classModel.setTeacherId(uid);
                    classModel.setStartDate(startDate);
                    classModel.setEndDate(endDate);
                    classModel.setSchedule(schedule);
                    classModel.setStartAt(startAt);
                    classModel.setEndAt(endAt);
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

    /** Giờ kết thúc phải sau giờ bắt đầu (strictly after). */
    private boolean isEndTimeAfterStartTime() {
        return toMinutes(endAt) > toMinutes(startAt);
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
