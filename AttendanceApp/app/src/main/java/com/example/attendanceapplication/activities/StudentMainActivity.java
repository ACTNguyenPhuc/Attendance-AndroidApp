package com.example.attendanceapplication.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.fragments.student.StudentDashboardFragment;
import com.example.attendanceapplication.fragments.student.StudentClassListFragment;
import com.example.attendanceapplication.fragments.student.StudentCalendarFragment;
import com.example.attendanceapplication.fragments.student.AttendanceHistoryFragment;
import com.example.attendanceapplication.fragments.shared.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class StudentMainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        setupBottomNavigation();
        loadFragment(new StudentDashboardFragment());
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment selected;
            if (id == R.id.nav_home) {
                selected = new StudentDashboardFragment();
            } else if (id == R.id.nav_classes) {
                selected = new StudentClassListFragment();
            } else if (id == R.id.nav_calendar) {
                selected = new StudentCalendarFragment();
            } else if (id == R.id.nav_history) {
                selected = new AttendanceHistoryFragment();
            } else if (id == R.id.nav_profile) {
                selected = new ProfileFragment();
            } else {
                return false;
            }
            loadFragment(selected);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
