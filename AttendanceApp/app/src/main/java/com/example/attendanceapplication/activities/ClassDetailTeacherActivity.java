package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.ShiftListAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

import android.view.Menu;
import android.view.MenuInflater;
import com.example.attendanceapplication.utils.ClassQRDialog;

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

        initViews();
        loadClassDetails();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "📤 Chia sẻ QR lớp")
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == 1) {
            ClassQRDialog.show(this, classId, className);
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
                case 0: tab.setText("📋 Buổi học"); break;
                case 1: tab.setText("👥 Sinh viên"); break;
                case 2: tab.setText("📊 Thống kê"); break;
            }
        }).attach();
    }

    private void loadClassDetails() {
        repo.getClassById(classId,
                classModel -> runOnUiThread(() -> {
                    tvClassName.setText(classModel.getClassName());
                    tvClassId.setText(classModel.getClassId());
                    tvSchedule.setText(classModel.getScheduleDisplay() +
                            "  " + classModel.getStartAt() + "-" + classModel.getEndAt());
                }),
                e -> {}
        );
    }
}
