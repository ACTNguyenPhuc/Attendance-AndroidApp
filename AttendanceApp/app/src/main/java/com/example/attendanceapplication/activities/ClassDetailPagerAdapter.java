package com.example.attendanceapplication.activities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.attendanceapplication.fragments.teacher.ShiftsTabFragment;
import com.example.attendanceapplication.fragments.teacher.StudentsTabFragment;
import com.example.attendanceapplication.fragments.teacher.StatsTabFragment;

public class ClassDetailPagerAdapter extends FragmentStateAdapter {

    private final String classId;
    private final String className;

    public ClassDetailPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                    String classId, String className) {
        super(fragmentActivity);
        this.classId   = classId;
        this.className = className;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Bundle args = new Bundle();
        args.putString("classId", classId);
        args.putString("className", className);

        Fragment fragment;
        switch (position) {
            case 1:  fragment = new StudentsTabFragment(); break;
            case 2:  fragment = new StatsTabFragment();    break;
            default: fragment = new ShiftsTabFragment();   break;
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getItemCount() { return 3; }
}
