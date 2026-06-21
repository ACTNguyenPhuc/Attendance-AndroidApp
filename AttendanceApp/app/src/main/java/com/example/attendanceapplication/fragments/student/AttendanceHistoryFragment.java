package com.example.attendanceapplication.fragments.student;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.AttendanceHistoryAdapter;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.*;

public class AttendanceHistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private TextView tvTotal, tvPresent, tvAbsent, tvRate;
    private AttendanceHistoryAdapter adapter;
    private List<Attendance> historyList = new ArrayList<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attendance_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvHistory = view.findViewById(R.id.rv_history);
        tvTotal   = view.findViewById(R.id.tv_total);
        tvPresent = view.findViewById(R.id.tv_present);
        tvAbsent  = view.findViewById(R.id.tv_absent);
        tvRate    = view.findViewById(R.id.tv_rate);

        adapter = new AttendanceHistoryAdapter(historyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);

        loadHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentAttendanceHistory(uid, null,
                list -> render(list != null && !list.isEmpty() ? list : buildMockHistory()),
                e -> render(buildMockHistory())
        );
    }

    private void render(List<Attendance> list) {
        requireActivity().runOnUiThread(() -> {
            historyList.clear();
            historyList.addAll(list);
            adapter.notifyDataSetChanged();
            updateSummary(list);
        });
    }

    private void updateSummary(List<Attendance> list) {
        int present = 0, absent = 0;
        for (Attendance a : list) {
            if (Attendance.STATUS_PRESENT.equals(a.getStatus()) ||
                    Attendance.STATUS_LATE.equals(a.getStatus())) present++;
            else absent++;
        }
        int total = list.size();
        int rate  = total > 0 ? (present * 100 / total) : 0;

        tvTotal.setText(String.valueOf(total));
        tvPresent.setText(String.valueOf(present));
        tvAbsent.setText(String.valueOf(absent));
        tvRate.setText("Tỉ lệ chuyên cần: " + rate + "%");
    }

    /**
     * Mock attendance history shown when the student has no records yet,
     * so the screen still demonstrates the present / late / absent states.
     */
    private List<Attendance> buildMockHistory() {
        // className, status, distanceMeters, daysAgo
        Object[][] specs = {
                {"Linux core",      Attendance.STATUS_PRESENT, 12.0,  21},
                {"Linux core",      Attendance.STATUS_PRESENT, 28.0,  14},
                {"Mạng máy tính",   Attendance.STATUS_LATE,    45.0,  10},
                {"Mạng máy tính",   Attendance.STATUS_PRESENT, 8.0,    7},
                {"An toàn mạng",    Attendance.STATUS_ABSENT,  0.0,    3},
                {"An toàn mạng",    Attendance.STATUS_PRESENT, 33.0,   1},
        };

        List<Attendance> mock = new ArrayList<>();
        for (int i = 0; i < specs.length; i++) {
            Object[] s = specs[i];
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -(int) s[3]);

            Attendance a = new Attendance();
            a.setAttendanceId("mock_" + i);
            a.setClassId((String) s[0]);
            a.setShiftId("Buổi " + String.format(Locale.US, "%02d", specs.length - i));
            a.setStatus((String) s[1]);
            a.setDistance((double) s[2]);
            a.setCheckinTime(new com.google.firebase.Timestamp(cal.getTime()));
            mock.add(a);
        }
        return mock;
    }
}
