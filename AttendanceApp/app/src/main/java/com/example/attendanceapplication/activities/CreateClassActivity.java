package com.example.attendanceapplication.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.example.attendanceapplication.utils.RequiredFieldUtils;
import com.example.attendanceapplication.utils.StudyShiftOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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
    // Ca học theo từng thứ (2=T2 … 8=CN). Firestore vẫn lưu cặp startAt/endAt.
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
        markRequiredFields();
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

    private void markRequiredFields() {
        RequiredFieldUtils.markRequired(this,
                (TextInputLayout) findViewById(R.id.til_class_name));
        RequiredFieldUtils.markRequired(this,
                (TextInputLayout) findViewById(R.id.til_class_id));
        RequiredFieldUtils.markRequired(this,
                (TextInputLayout) findViewById(R.id.til_room));
        RequiredFieldUtils.markRequired(this, (TextView) findViewById(R.id.tv_start_date_label));
        RequiredFieldUtils.markRequired(this, (TextView) findViewById(R.id.tv_end_date_label));
        RequiredFieldUtils.markRequired(this, (TextView) findViewById(R.id.tv_schedule_label));
        RequiredFieldUtils.markRequired(this, (TextView) findViewById(R.id.tv_day_times_label));
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
            TextInputLayout tilShift = row.findViewById(R.id.til_shift);
            MaterialAutoCompleteTextView shiftDropdown = row.findViewById(R.id.dropdown_shift);

            tvLabel.setText(dayLabel(day));
            RequiredFieldUtils.markRequired(this, tilShift);
            shiftDropdown.setAdapter(createShiftAdapter());

            StudyShiftOptions.Option selectedOption = StudyShiftOptions.findByTimes(
                    startByDay.get(day), endByDay.get(day));
            if (selectedOption != null) {
                shiftDropdown.setText(selectedOption.getLabel(), false);
            }
            shiftDropdown.setOnItemClickListener((parent, view, position, id) -> {
                StudyShiftOptions.Option option = StudyShiftOptions.get(position);
                startByDay.put(day, option.getStartAt());
                endByDay.put(day, option.getEndAt());
            });

            llDayTimes.addView(row);
        }
    }

    private ArrayAdapter<String> createShiftAdapter() {
        return new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                StudyShiftOptions.getLabels());
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
        etClassId.setError(null);
        String className = etClassName.getText().toString().trim();
        String classId   = etClassId.getText().toString().trim();
        String room      = etRoom.getText().toString().trim();
        TextInputLayout tilRoom = findViewById(R.id.til_room);
        tilRoom.setError(null);

        if (className.isEmpty() || classId.isEmpty() || room.isEmpty()
                || startDate.isEmpty() || endDate.isEmpty()) {
            if (room.isEmpty()) {
                tilRoom.setError("Vui lòng nhập phòng học");
            }
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
        // Mỗi ngày bắt buộc chọn một trong các ca học cố định.
        for (int day : schedule) {
            String s = startByDay.get(day), e = endByDay.get(day);
            if (!StudyShiftOptions.isValid(s, e)) {
                Toast.makeText(this, "Vui lòng chọn ca học cho " + dayLabel(day), Toast.LENGTH_SHORT).show();
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
                    if (err instanceof FirebaseRepository.TeacherScheduleConflictException) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Xung đột lịch giảng")
                                .setMessage(err.getMessage())
                                .setPositiveButton("Đã hiểu", null)
                                .show();
                        return;
                    }
                    if (err instanceof FirebaseRepository.RoomConflictException) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Xung đột phòng học")
                                .setMessage(err.getMessage())
                                .setPositiveButton("Đã hiểu", null)
                                .show();
                        return;
                    }
                    boolean duplicateClassId = err instanceof FirebaseRepository.DuplicateClassIdException;
                    String message = duplicateClassId ? err.getMessage() : "Lỗi: " + err.getMessage();
                    if (duplicateClassId) {
                        etClassId.setError("Mã lớp đã tồn tại");
                        etClassId.requestFocus();
                    }
                    Snackbar.make(btnCreate, message, Snackbar.LENGTH_LONG).show();
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

    private void finishWithCreatedClass(String classId) {
        Intent result = new Intent();
        result.putExtra(EXTRA_CREATED_CLASS_ID, classId);
        setResult(RESULT_OK, result);
        finish();
    }
}
