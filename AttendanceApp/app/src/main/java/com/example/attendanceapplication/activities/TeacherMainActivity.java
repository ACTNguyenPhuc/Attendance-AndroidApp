package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.fragments.teacher.TeacherDashboardFragment;
import com.example.attendanceapplication.fragments.teacher.TeacherClassListFragment;
import com.example.attendanceapplication.fragments.teacher.TeacherCalendarFragment;
import com.example.attendanceapplication.fragments.shared.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class TeacherMainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        setupBottomNavigation();

        // Show dashboard by default
        loadFragment(new TeacherDashboardFragment());
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment selected;
            if (id == R.id.nav_home) {
                selected = new TeacherDashboardFragment();
            } else if (id == R.id.nav_classes) {
                selected = new TeacherClassListFragment();
            } else if (id == R.id.nav_calendar) {
                selected = new TeacherCalendarFragment();
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
