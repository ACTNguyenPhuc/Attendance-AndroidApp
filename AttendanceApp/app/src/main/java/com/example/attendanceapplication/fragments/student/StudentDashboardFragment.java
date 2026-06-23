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
import com.example.attendanceapplication.adapters.StudentClassProgressAdapter;
import com.example.attendanceapplication.adapters.TodaySessionAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.*;

public class StudentDashboardFragment extends Fragment {

    private TextView tvGreeting, tvStudentCode, tvAvatar, tvDate;
    private TextView tvTotalClasses, tvAttendanceRate, tvTodayCount;
    private TextView tvTodayEmpty, tvClassesEmpty;
    private ImageButton btnScanAttendance;
    private RecyclerView rvToday, rvClasses;

    private TodaySessionAdapter todayAdapter;
    private StudentClassProgressAdapter classAdapter;
    private final List<Shift> todayShifts = new ArrayList<>();
    private final List<ClassModel> classList = new ArrayList<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvGreeting       = view.findViewById(R.id.tv_greeting);
        tvStudentCode    = view.findViewById(R.id.tv_student_code);
        tvAvatar         = view.findViewById(R.id.tv_avatar);
        tvDate           = view.findViewById(R.id.tv_date);
        tvTotalClasses   = view.findViewById(R.id.tv_total_classes);
        tvAttendanceRate = view.findViewById(R.id.tv_attendance_rate);
        tvTodayCount     = view.findViewById(R.id.tv_today_count);
        tvTodayEmpty     = view.findViewById(R.id.tv_today_empty);
        tvClassesEmpty   = view.findViewById(R.id.tv_classes_empty);
        btnScanAttendance = view.findViewById(R.id.btn_scan_attendance);
        rvToday          = view.findViewById(R.id.rv_today);
        rvClasses        = view.findViewById(R.id.rv_classes);

        todayAdapter = new TodaySessionAdapter(todayShifts, shift ->
                startActivity(new Intent(requireContext(), ScanAttendanceActivity.class)));
        rvToday.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvToday.setAdapter(todayAdapter);

        classAdapter = new StudentClassProgressAdapter(classList, cls -> {
            Intent i = new Intent(requireContext(), ClassDetailStudentActivity.class);
            i.putExtra("classId", cls.getClassId());
            i.putExtra("className", cls.getClassName());
            startActivity(i);
        });
        rvClasses.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvClasses.setAdapter(classAdapter);

        if (btnScanAttendance != null) {
            btnScanAttendance.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), ScanAttendanceActivity.class)));
        }

        displayDate();
        loadProfile();
        loadClasses();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Cập nhật lại trạng thái buổi hôm nay/tỉ lệ sau khi quay lại từ màn điểm danh.
        if (!classList.isEmpty()) computeShiftsAndRates();
    }

    private void displayDate() {
        String[] daysVN = {"Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
        String dateStr = daysVN[dow] + ", " +
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        tvDate.setText(dateStr);
    }

    private void loadProfile() {
        if (!isAdded() || FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getUserProfile(uid,
                user -> {
                    if (!isAdded() || getActivity() == null) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        tvGreeting.setText("Xin chào, " + user.getName());
                        tvStudentCode.setText("Mã SV: "
                                + (user.getStudentCode() != null ? user.getStudentCode() : ""));
                        tvAvatar.setText(getInitials(user.getName()));
                    });
                },
                e -> {}
        );
    }

    private void loadClasses() {
        if (!isAdded() || FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            if (!isAdded()) return;
            classList.clear();
            classList.addAll(classes);
            classAdapter.notifyDataSetChanged();
            tvTotalClasses.setText(String.valueOf(classes.size()));
            tvClassesEmpty.setVisibility(classes.isEmpty() ? View.VISIBLE : View.GONE);
            rvClasses.setVisibility(classes.isEmpty() ? View.GONE : View.VISIBLE);

            if (classes.isEmpty()) {
                todayShifts.clear();
                todayAdapter.notifyDataSetChanged();
                tvTodayCount.setText("0");
                tvAttendanceRate.setText("0%");
                tvTodayEmpty.setVisibility(View.VISIBLE);
                rvToday.setVisibility(View.GONE);
            } else {
                computeShiftsAndRates();
            }
        });
    }

    private void computeShiftsAndRates() {
        if (!isAdded() || FirebaseAuth.getInstance().getCurrentUser() == null) return;
        final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        List<String> classIds = new ArrayList<>();
        for (ClassModel c : classList) {
            if (c.getClassId() != null && !c.getClassId().isEmpty()) classIds.add(c.getClassId());
        }
        if (classIds.isEmpty()) return;

        final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        repo.getShiftsForClasses(classIds, allShifts -> {
            if (!isAdded() || getActivity() == null) return;

            // Đếm số buổi đã kết thúc theo từng lớp (mẫu số để tính tỉ lệ).
            final Map<String, Integer> completedByClass = new HashMap<>();
            final List<Shift> todays = new ArrayList<>();
            for (Shift s : allShifts) {
                if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
                    Integer cur = completedByClass.get(s.getClassId());
                    completedByClass.put(s.getClassId(), (cur == null ? 0 : cur) + 1);
                }
                if (today.equals(s.getDate())) todays.add(s);
            }
            todays.sort((a, b) -> {
                String sa = a.getStartAt() == null ? "" : a.getStartAt();
                String sb = b.getStartAt() == null ? "" : b.getStartAt();
                return sa.compareTo(sb);
            });

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                todayShifts.clear();
                todayShifts.addAll(todays);
                todayAdapter.notifyDataSetChanged();
                tvTodayCount.setText(String.valueOf(todays.size()));
                tvTodayEmpty.setVisibility(todays.isEmpty() ? View.VISIBLE : View.GONE);
                rvToday.setVisibility(todays.isEmpty() ? View.GONE : View.VISIBLE);
            });

            loadTodayAttendanceState(uid, todays);
            loadRates(uid, completedByClass);
        });
    }

    /** Xác định buổi hôm nay nào sinh viên đã điểm danh (để đổi badge / ẩn nút). */
    private void loadTodayAttendanceState(String uid, List<Shift> todays) {
        if (todays.isEmpty()) return;
        final Set<String> attended = Collections.synchronizedSet(new HashSet<>());
        final int[] pending = {todays.size()};
        for (Shift s : todays) {
            repo.getShiftAttendances(s.getShiftId(),
                    list -> {
                        for (com.example.attendanceapplication.models.Attendance a : list) {
                            if (uid.equals(a.getStudentId())) { attended.add(s.getShiftId()); break; }
                        }
                        pending[0]--;
                        if (pending[0] == 0 && isAdded() && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                if (isAdded()) todayAdapter.setAttendedShiftIds(attended);
                            });
                        }
                    },
                    e -> {
                        pending[0]--;
                        if (pending[0] == 0 && isAdded() && getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                if (isAdded()) todayAdapter.setAttendedShiftIds(attended);
                            });
                        }
                    });
        }
    }

    /** Tính tỉ lệ điểm danh từng lớp và tỉ lệ tổng. */
    private void loadRates(String uid, Map<String, Integer> completedByClass) {
        final int[] pending = {classList.size()};
        final int[] attendedTotal = {0};
        final int[] completedTotal = {0};

        for (ClassModel c : classList) {
            final String classId = c.getClassId();
            final int completed = completedByClass.containsKey(classId) ? completedByClass.get(classId) : 0;
            repo.getStudentAttendanceCount(classId, uid,
                    count -> {
                        int attendedForRate = Math.min(count, completed);
                        int rate = completed == 0 ? -1
                                : Math.round(attendedForRate * 100f / completed);
                        synchronized (attendedTotal) {
                            attendedTotal[0] += attendedForRate;
                            completedTotal[0] += completed;
                            pending[0]--;
                        }
                        if (!isAdded() || getActivity() == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            classAdapter.setRate(classId, rate);
                            if (pending[0] == 0) {
                                int overall = completedTotal[0] == 0 ? 0
                                        : Math.round(attendedTotal[0] * 100f / completedTotal[0]);
                                tvAttendanceRate.setText(overall + "%");
                            }
                        });
                    },
                    e -> {
                        synchronized (attendedTotal) { pending[0]--; }
                    });
        }
    }

    private String getInitials(String name) {
        if (name == null) return "SV";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "SV";
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
