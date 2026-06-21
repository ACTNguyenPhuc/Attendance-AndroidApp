package com.example.attendanceapplication.fragments.teacher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.CreateClassActivity;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.adapters.ClassCardAdapter;
import com.example.attendanceapplication.adapters.ShiftHomeAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherDashboardFragment extends Fragment {

    private TextView tvGreeting, tvDate, tvAvatar, tvTotalClasses, tvTodaySessions, tvPendingOpen;
    private RecyclerView rvClasses;
    private RecyclerView rvTodayShifts;
    private TextView tvViewAllShifts, tvCreateClass, tvTodayEmpty;
    private ClassCardAdapter adapter;
    private List<ClassModel> classList = new ArrayList<>();
    private ShiftHomeAdapter shiftHomeAdapter;
    private final List<Shift> todayShiftList = new ArrayList<>();
    private LiveData<List<ClassModel>> classLiveData;
    private ActivityResultLauncher<Intent> createClassLauncher;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_dashboard, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createClassLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                    addCreatedClass(result.getData()
                            .getStringExtra(CreateClassActivity.EXTRA_CREATED_CLASS_ID));
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadUserData();
        loadClasses();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!classList.isEmpty()) {
            loadTodayShifts(new ArrayList<>(classList));
        }
    }

    private void initViews(View view) {
        tvGreeting    = view.findViewById(R.id.tv_greeting);
        tvDate        = view.findViewById(R.id.tv_date);
        tvAvatar      = view.findViewById(R.id.tv_avatar);
        tvTotalClasses = view.findViewById(R.id.tv_total_classes);
        tvTodaySessions = view.findViewById(R.id.tv_today_sessions);
        tvPendingOpen = view.findViewById(R.id.tv_pending_open);
        rvClasses     = view.findViewById(R.id.rv_classes);
        rvTodayShifts = view.findViewById(R.id.rv_today_shifts);
        tvViewAllShifts = view.findViewById(R.id.tv_view_all_shifts);
        tvCreateClass = view.findViewById(R.id.tv_create_class);
        tvTodayEmpty  = view.findViewById(R.id.tv_today_empty);

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

        shiftHomeAdapter = new ShiftHomeAdapter(todayShiftList, shift -> {
            Intent intent = new Intent(requireContext(), SessionManagementActivity.class);
            intent.putExtra(SessionManagementActivity.EXTRA_SHIFT_ID, shift.getShiftId());
            intent.putExtra(SessionManagementActivity.EXTRA_CLASS_ID, shift.getClassId());
            intent.putExtra(SessionManagementActivity.EXTRA_CLASS_NAME, shift.getClassName());
            startActivity(intent);
        });
        rvTodayShifts.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        rvTodayShifts.setAdapter(shiftHomeAdapter);

        tvCreateClass.setOnClickListener(v ->
                createClassLauncher.launch(new Intent(requireContext(), CreateClassActivity.class))
        );

        tvViewAllShifts.setOnClickListener(v -> {
            if (getActivity() == null) return;
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_calendar);
        });

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
                        requireActivity().runOnUiThread(() -> {
                            tvGreeting.setText("Xin chào, " + user.getName());
                            tvAvatar.setText(getInitials(user.getName()));
                        });
                    }
                },
                e -> {}
        );
    }

    private void loadClasses() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (classLiveData == null) {
            classLiveData = repo.getTeacherClasses(uid);
        }
        classLiveData.removeObservers(getViewLifecycleOwner());
        classLiveData.observe(getViewLifecycleOwner(), classes -> {
            classList.clear();
            classList.addAll(classes);
            adapter.notifyDataSetChanged();
            tvTotalClasses.setText(String.valueOf(classes.size()));

            loadTodayShifts(classes);
            loadStudentCounts();
        });
    }

    private void loadStudentCounts() {
        for (ClassModel c : classList) {
            if (c.getClassId() == null || c.getClassId().isEmpty()) continue;
            repo.getClassStudents(c.getClassId(),
                    students -> requireActivity().runOnUiThread(() -> {
                        c.setStudentCount(students.size());
                        notifyClassUpdated(c.getClassId());
                    }),
                    e -> {}
            );
        }
    }

    private void addCreatedClass(String classId) {
        if (classId == null || classId.isEmpty()) return;
        repo.getClassById(classId,
                createdClass -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        for (int i = classList.size() - 1; i >= 0; i--) {
                            if (classId.equals(classList.get(i).getClassId())) {
                                classList.remove(i);
                            }
                        }
                        classList.add(0, createdClass);
                        adapter.notifyDataSetChanged();
                        tvTotalClasses.setText(String.valueOf(classList.size()));
                        loadTodayShifts(new ArrayList<>(classList));
                    });
                },
                e -> {
                    if (isAdded()) loadClasses();
                }
        );
    }

    private void notifyClassUpdated(String classId) {
        for (int i = 0; i < classList.size(); i++) {
            ClassModel c = classList.get(i);
            if (classId.equals(c.getClassId())) {
                adapter.notifyItemChanged(i);
                return;
            }
        }
    }

    private void loadTodayShifts(List<ClassModel> classes) {
        List<String> classIds = new ArrayList<>();
        for (ClassModel c : classes) {
            if (c.getClassId() != null && !c.getClassId().isEmpty()) classIds.add(c.getClassId());
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        repo.getShiftsByDate(classIds, today, shifts -> {
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                todayShiftList.clear();
                todayShiftList.addAll(shifts);
                shiftHomeAdapter.notifyDataSetChanged();

                tvTodaySessions.setText(String.valueOf(shifts.size()));

                // "Chờ mở" = buổi hôm nay CHƯA mở điểm danh (status = upcoming).
                // Không tính buổi đang diễn ra (ongoing) hay đã kết thúc (completed),
                // dù completed cũng có attendanceOpened = false.
                int pendingOpen = 0;
                for (Shift s : shifts) {
                    if (Shift.STATUS_UPCOMING.equals(s.getStatus())) pendingOpen++;
                }
                tvPendingOpen.setText(String.valueOf(pendingOpen));

                tvTodayEmpty.setVisibility(shifts.isEmpty() ? View.VISIBLE : View.GONE);
                rvTodayShifts.setVisibility(shifts.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private String getInitials(String name) {
        if (name == null) return "GV";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "GV";
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.getDefault());
        }
        String first = parts[0];
        String last = parts[parts.length - 1];
        String initials = (first.isEmpty() ? "" : first.substring(0, 1))
                + (last.isEmpty() ? "" : last.substring(0, 1));
        return initials.toUpperCase(Locale.getDefault());
    }
}
