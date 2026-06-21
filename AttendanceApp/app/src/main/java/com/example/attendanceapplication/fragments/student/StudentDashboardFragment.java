package com.example.attendanceapplication.fragments.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.*;
import com.example.attendanceapplication.adapters.ClassCardAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.*;

public class StudentDashboardFragment extends Fragment {

    private TextView tvGreeting, tvStudentCode, tvTotalClasses;
    private Button btnJoinClass;
    private ImageButton btnScanAttendance;
    private RecyclerView rvClasses;
    private ClassCardAdapter adapter;
    private List<ClassModel> classList = new ArrayList<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvGreeting     = view.findViewById(R.id.tv_greeting);
        tvStudentCode  = view.findViewById(R.id.tv_student_code);
        tvTotalClasses = view.findViewById(R.id.tv_total_classes);
        btnJoinClass   = view.findViewById(R.id.btn_join_class);
        btnScanAttendance = view.findViewById(R.id.btn_scan_attendance);
        rvClasses      = view.findViewById(R.id.rv_classes);

        adapter = new ClassCardAdapter(classList, cls -> {
            Intent intent = new Intent(requireContext(),
                    com.example.attendanceapplication.activities.ClassDetailStudentActivity.class);
            intent.putExtra("classId", cls.getClassId());
            intent.putExtra("className", cls.getClassName());
            startActivity(intent);
        });
        rvClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClasses.setAdapter(adapter);

        btnJoinClass.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), JoinClassActivity.class)));

        if (btnScanAttendance != null) {
            btnScanAttendance.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ScanAttendanceActivity.class)));
        }

        loadProfile();
        loadClasses();
    }

    private void loadProfile() {
        if (!isAdded()) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getUserProfile(uid,
                user -> {
                    if (!isAdded()) return;
                    android.app.Activity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (!isAdded()) return;
                        tvGreeting.setText("Xin chào, " + user.getName());
                        tvStudentCode.setText(user.getStudentCode());
                    });
                },
                e -> {}
        );
    }

    private void loadClasses() {
        if (!isAdded()) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            if (!isAdded()) return;
            classList.clear();
            classList.addAll(classes);
            adapter.notifyDataSetChanged();
            tvTotalClasses.setText(String.valueOf(classes.size()));
        });
    }
}
