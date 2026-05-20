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

    private void loadHistory() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentAttendanceHistory(uid, null,
                list -> {
                    historyList.clear();
                    historyList.addAll(list);
                    adapter.notifyDataSetChanged();
                    updateSummary(list);
                },
                e -> {}
        );
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

        int finalPresent = present;
        int finalAbsent = absent;
        requireActivity().runOnUiThread(() -> {
            tvTotal.setText(String.valueOf(total));
            tvPresent.setText(String.valueOf(finalPresent));
            tvAbsent.setText(String.valueOf(finalAbsent));
            tvRate.setText(rate + "%");
        });
    }
}
