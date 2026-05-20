package com.example.attendanceapplication.fragments.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.CreateClassActivity;
import com.example.attendanceapplication.adapters.ClassCardAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherDashboardFragment extends Fragment {

    private TextView tvGreeting, tvDate, tvTotalClasses, tvTodaySessions;
    private RecyclerView rvClasses;
    private FloatingActionButton fabCreateClass;
    private ClassCardAdapter adapter;
    private List<ClassModel> classList = new ArrayList<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadUserData();
        loadClasses();
    }

    private void initViews(View view) {
        tvGreeting    = view.findViewById(R.id.tv_greeting);
        tvDate        = view.findViewById(R.id.tv_date);
        tvTotalClasses = view.findViewById(R.id.tv_total_classes);
        tvTodaySessions = view.findViewById(R.id.tv_today_sessions);
        rvClasses     = view.findViewById(R.id.rv_classes);
        fabCreateClass = view.findViewById(R.id.fab_create_class);

        adapter = new ClassCardAdapter(classList, classModel -> {
            // Navigate to class detail
            Intent intent = new Intent(requireContext(),
                    com.example.attendanceapplication.activities.ClassDetailTeacherActivity.class);
            intent.putExtra("classId", classModel.getClassId());
            intent.putExtra("className", classModel.getClassName());
            startActivity(intent);
        });
        rvClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClasses.setAdapter(adapter);

        fabCreateClass.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreateClassActivity.class))
        );

        // Display today's date
        String[] daysVN = {"Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
        String dateStr = daysVN[dow] + ", " +
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        tvDate.setText(dateStr);
    }

    private void loadUserData() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getUserProfile(uid,
                user -> {
                    if (getActivity() != null) {
                        requireActivity().runOnUiThread(() ->
                                tvGreeting.setText("Xin chào, " + user.getName()));
                    }
                },
                e -> {}
        );
    }

    private void loadClasses() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getTeacherClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            classList.clear();
            classList.addAll(classes);
            adapter.notifyDataSetChanged();
            tvTotalClasses.setText(String.valueOf(classes.size()));
        });
    }
}
