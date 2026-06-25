package com.example.attendanceapplication.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.view.Menu;
import com.example.attendanceapplication.utils.ClassQRDialog;
import com.example.attendanceapplication.fragments.teacher.StudentsTabFragment;

public class ClassDetailTeacherActivity extends AppCompatActivity {

    private String classId, className;
    private TextView tvClassName, tvClassId, tvSchedule, tvStudentCount;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_detail_teacher);

        classId   = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(className);
        }
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon.setTint(ContextCompat.getColor(this, R.color.white));
        }
        initViews();
        loadClassDetails();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_class_detail_teacher, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == R.id.action_qr) {
            ClassQRDialog.show(this, classId, className);
            return true;
        }
        if (item.getItemId() == R.id.action_add_shift) {
            showAddMakeupShiftDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_add_student) {
            openAddStudent();
            return true;
        }
        if (item.getItemId() == R.id.action_edit_class) {
            showEditClassDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        tvClassName    = findViewById(R.id.tv_class_name);
        tvClassId      = findViewById(R.id.tv_class_id);
        tvSchedule     = findViewById(R.id.tv_schedule);
        tvStudentCount = findViewById(R.id.tv_student_count);
        tabLayout      = findViewById(R.id.tab_layout);
        viewPager      = findViewById(R.id.view_pager);

        // Setup ViewPager2 with tabs
        ClassDetailPagerAdapter adapter = new ClassDetailPagerAdapter(this, classId, className);
        viewPager.setAdapter(adapter);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Buổi học");
                    tab.setIcon(R.drawable.ic_tab_shifts);
                    break;
                case 1:
                    tab.setText("Sinh viên");
                    tab.setIcon(R.drawable.ic_tab_students);
                    break;
                case 2:
                    tab.setText("Thống kê");
                    tab.setIcon(R.drawable.ic_tab_stats);
                    break;
            }
        }).attach();
    }

    private void loadClassDetails() {
        repo.getClassById(classId,
                classModel -> runOnUiThread(() -> {
                    tvClassName.setText(classModel.getClassName());
                    tvClassId.setText(classModel.getClassId());
                    tvSchedule.setText(classModel.getScheduleTimeDisplay());
                }),
                e -> {}
        );
    }

    private void openAddStudent() {
        viewPager.setCurrentItem(1, true);
        viewPager.post(() -> {
            androidx.fragment.app.Fragment f = getSupportFragmentManager().findFragmentByTag("f1");
            if (f instanceof StudentsTabFragment) {
                ((StudentsTabFragment) f).requestAddStudentDialog();
            }
        });
    }

    private void showEditClassDialog() {
        View content = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_class, null, false);
        TextInputEditText etName = content.findViewById(R.id.et_class_name);
        TextInputEditText etRoom = content.findViewById(R.id.et_room);

        etName.setText(tvClassName.getText());

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa lớp học")
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (d, which) -> {
                    String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
                    String newRoom = etRoom.getText() == null ? "" : etRoom.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Tên lớp không được rỗng", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateClassInfo(newName, newRoom);
                })
                .show();
    }

    /** Dialog tạo ca học bù: nhập ngày, giờ bắt đầu/kết thúc, phòng và tiêu đề (tùy chọn). */
    private void showAddMakeupShiftDialog() {
        View content = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_makeup_shift, null, false);
        TextView tvDate  = content.findViewById(R.id.tv_date);
        TextView tvStart = content.findViewById(R.id.tv_start_time);
        TextView tvEnd   = content.findViewById(R.id.tv_end_time);
        TextInputEditText etRoom  = content.findViewById(R.id.et_room);
        TextInputEditText etTitle = content.findViewById(R.id.et_title);

        // picked = { date, startAt, endAt }
        final String[] picked = {"", "", ""};

        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (dp, y, m, d) -> {
                picked[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                tvDate.setText(picked[0]);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        tvStart.setOnClickListener(v -> pickTime(picked, 1, tvStart));
        tvEnd.setOnClickListener(v -> pickTime(picked, 2, tvEnd));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Thêm ca học bù")
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Tạo", null)
                .create();
        dialog.show();

        // Override để validate mà không tự đóng dialog khi dữ liệu chưa hợp lệ.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String date = picked[0], start = picked[1], end = picked[2];
            if (date.isEmpty() || start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ngày và giờ học", Toast.LENGTH_SHORT).show();
                return;
            }
            if (toMinutes(end) <= toMinutes(start)) {
                Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show();
                return;
            }
            String room  = etRoom.getText()  == null ? "" : etRoom.getText().toString().trim();
            String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();

            repo.createMakeupShift(classId, date, start, end, room, title,
                    id -> runOnUiThread(() -> {
                        Toast.makeText(this, "Đã thêm ca học bù", Toast.LENGTH_SHORT).show();
                        viewPager.setCurrentItem(0, true); // chuyển sang tab "Buổi học"
                        dialog.dismiss();
                    }),
                    e -> runOnUiThread(() -> {
                        String msg = (e instanceof FirebaseRepository.ShiftConflictException)
                                ? e.getMessage()
                                : "Lỗi: " + e.getMessage();
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    })
            );
        });
    }

    private void pickTime(String[] picked, int idx, TextView target) {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(this, (tp, h, m) -> {
            String t = String.format(Locale.US, "%02d:%02d", h, m);
            picked[idx] = t;
            target.setText(t);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private int toMinutes(String time) {
        try {
            String[] p = time.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private void updateClassInfo(String name, String room) {
        repo.updateClassInfo(classId, name, room,
                r -> runOnUiThread(() -> {
                    className = name;
                    tvClassName.setText(name);
                    Toast.makeText(this, "Đã cập nhật lớp", Toast.LENGTH_SHORT).show();
                }),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, "Cập nhật thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show())
        );
    }
}
