package com.example.attendanceapplication.fragments.student;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.AttendanceHistoryAdapter;
import com.example.attendanceapplication.adapters.AttendanceHistoryAdapter.HistoryItem;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.*;

public class AttendanceHistoryFragment extends Fragment {

    private Spinner spinnerClass;
    private RecyclerView rvHistory;
    private TextView tvTotal, tvPresent, tvAbsent, tvRate, tvEmpty;
    private CircularProgressIndicator progressRate;

    private AttendanceHistoryAdapter adapter;
    private final List<Object> rows = new ArrayList<>();

    private final List<ClassModel> classes = new ArrayList<>();
    private final Map<String, String> classNameById = new HashMap<>();
    private String selectedClassId = null; // null = tất cả lớp

    // Dữ liệu thật đã tải, để lọc lại trong bộ nhớ khi đổi lớp.
    private final List<Shift> completedShifts = new ArrayList<>();
    private final Map<String, Attendance> attendanceByShift = new HashMap<>();

    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attendance_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        spinnerClass = view.findViewById(R.id.spinner_class);
        rvHistory    = view.findViewById(R.id.rv_history);
        tvTotal      = view.findViewById(R.id.tv_total);
        tvPresent    = view.findViewById(R.id.tv_present);
        tvAbsent     = view.findViewById(R.id.tv_absent);
        tvRate       = view.findViewById(R.id.tv_rate);
        tvEmpty      = view.findViewById(R.id.tv_empty);
        progressRate = view.findViewById(R.id.progress_rate);

        adapter = new AttendanceHistoryAdapter(rows);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);

        loadClasses();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!classes.isEmpty()) loadData();
    }

    private void loadClasses() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), cs -> {
            if (!isAdded()) return;
            classes.clear();
            classes.addAll(cs);
            classNameById.clear();
            for (ClassModel c : cs) classNameById.put(c.getClassId(), c.getClassName());
            setupSpinner();
            loadData();
        });
    }

    private void setupSpinner() {
        List<String> names = new ArrayList<>();
        names.add("Tất cả lớp học");
        for (ClassModel c : classes) names.add(c.getClassName());

        ArrayAdapter<String> sa = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClass.setAdapter(sa);

        spinnerClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                selectedClassId = pos == 0 ? null : classes.get(pos - 1).getClassId();
                rebuild();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadData() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        List<String> classIds = new ArrayList<>();
        for (ClassModel c : classes) {
            if (c.getClassId() != null && !c.getClassId().isEmpty()) classIds.add(c.getClassId());
        }
        if (classIds.isEmpty()) { rebuild(); return; }

        repo.getShiftsForClasses(classIds, shifts -> {
            completedShifts.clear();
            for (Shift s : shifts) {
                if (Shift.STATUS_COMPLETED.equals(s.getStatus())) completedShifts.add(s);
            }
            repo.getStudentAttendanceHistory(uid, null,
                    atts -> {
                        attendanceByShift.clear();
                        for (Attendance a : atts) {
                            if (a.getShiftId() != null) attendanceByShift.put(a.getShiftId(), a);
                        }
                        if (isAdded() && getActivity() != null) requireActivity().runOnUiThread(this::rebuild);
                    },
                    e -> {
                        attendanceByShift.clear();
                        if (isAdded() && getActivity() != null) requireActivity().runOnUiThread(this::rebuild);
                    });
        });
    }

    /** Lọc theo lớp đang chọn, gom nhóm theo tháng và tính tổng hợp — toàn bộ trong bộ nhớ. */
    private void rebuild() {
        if (!isAdded()) return;

        List<Shift> filtered = new ArrayList<>();
        for (Shift s : completedShifts) {
            if (selectedClassId == null || selectedClassId.equals(s.getClassId())) filtered.add(s);
        }
        // Mới nhất lên đầu (chuỗi yyyy-MM-dd và HH:mm so sánh được theo thời gian).
        filtered.sort((a, b) -> {
            String da = a.getDate() == null ? "" : a.getDate();
            String dbb = b.getDate() == null ? "" : b.getDate();
            int c = dbb.compareTo(da);
            if (c != 0) return c;
            String sa = a.getStartAt() == null ? "" : a.getStartAt();
            String sb = b.getStartAt() == null ? "" : b.getStartAt();
            return sb.compareTo(sa);
        });

        rows.clear();
        int present = 0, absent = 0;
        String currentMonth = null;
        for (Shift s : filtered) {
            String monthKey = monthHeaderOf(s.getDate());
            if (monthKey != null && !monthKey.equals(currentMonth)) {
                currentMonth = monthKey;
                rows.add(monthKey);
            }

            Attendance att = attendanceByShift.get(s.getShiftId());
            HistoryItem it = new HistoryItem();
            it.className = classNameById.containsKey(s.getClassId())
                    ? classNameById.get(s.getClassId()) : s.getClassName();
            it.label = (s.getContent() != null && !s.getContent().isEmpty()) ? s.getContent()
                    : (s.getTitle() != null && !s.getTitle().isEmpty() ? s.getTitle() : "Buổi học");
            it.dateText = displayDate(s.getDate());

            if (att != null) {
                it.hasAttendance = true;
                it.status = Attendance.STATUS_LATE.equals(att.getStatus())
                        ? Attendance.STATUS_LATE : Attendance.STATUS_PRESENT;
                it.distance = att.getDistance();
                it.latitude = att.getLatitude();
                it.longitude = att.getLongitude();
                it.checkinTime = att.getCheckinTime() != null ? att.getCheckinTime().toDate() : null;
                present++;
            } else {
                it.hasAttendance = false;
                it.status = Attendance.STATUS_ABSENT;
                absent++;
            }
            rows.add(it);
        }
        adapter.notifyDataSetChanged();

        int total = filtered.size();
        int rate = total > 0 ? Math.round(present * 100f / total) : 0;
        tvTotal.setText(String.valueOf(total));
        tvPresent.setText(String.valueOf(present));
        tvAbsent.setText(String.valueOf(absent));
        tvRate.setText(rate + "%");
        progressRate.setProgressCompat(rate, true);
        progressRate.setIndicatorColor(ContextCompat.getColor(requireContext(), colorForRate(rate)));

        boolean empty = total == 0;
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private int colorForRate(int rate) {
        if (rate >= 80) return R.color.accent_green;
        if (rate >= 50) return R.color.warning_yellow;
        return R.color.error_red;
    }

    /** "yyyy-MM-dd" -> "tháng M năm yyyy"; null nếu không parse được. */
    private String monthHeaderOf(String date) {
        Calendar cal = parseDate(date);
        if (cal == null) return null;
        return "tháng " + (cal.get(Calendar.MONTH) + 1) + " năm " + cal.get(Calendar.YEAR);
    }

    /** "yyyy-MM-dd" -> "d/M/yyyy". */
    private String displayDate(String date) {
        Calendar cal = parseDate(date);
        if (cal == null) return date != null ? date : "";
        return cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1)
                + "/" + cal.get(Calendar.YEAR);
    }

    private Calendar parseDate(String date) {
        if (date == null || date.isEmpty()) return null;
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateKeyFormat.parse(date));
            return cal;
        } catch (Exception e) {
            return null;
        }
    }
}
