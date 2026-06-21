package com.example.attendanceapplication.fragments.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.adapters.ShiftListAdapter;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.activities.ShiftAttendanceListActivity;

import java.util.*;

public class ShiftsTabFragment extends Fragment {

    private RecyclerView rvShifts;
    private TextView tvEmpty;
    private ShiftListAdapter adapter;
    private List<Shift> shiftList = new ArrayList<>();
    private String classId, className;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    // After opening/closing an attendance session, reload the whole
    // ClassDetailTeacherActivity so every tab reflects the latest state.
    private ActivityResultLauncher<Intent> sessionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isAdded()) requireActivity().recreate();
                });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shifts_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            classId   = getArguments().getString("classId");
            className = getArguments().getString("className");
        }

        rvShifts = view.findViewById(R.id.rv_shifts);
        tvEmpty  = view.findViewById(R.id.tv_empty);

        adapter = new ShiftListAdapter(shiftList, classId, className,
                this::openSession,
                shift -> {
                    if (Shift.STATUS_ONGOING.equals(shift.getStatus()) || shift.isAttendanceOpened()) {
                        openSession(shift);
                    } else if (Shift.STATUS_COMPLETED.equals(shift.getStatus())) {
                        Intent intent = new Intent(requireContext(), ShiftAttendanceListActivity.class);
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_ID, shift.getShiftId());
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_ID, classId);
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_NAME, className);
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TITLE,
                                shift.getTitle() != null ? shift.getTitle() : shift.getClassName());
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TIME,
                                shift.getDayOfWeekDisplay() + "  " + shift.getStartAt() + " - " + shift.getEndAt());
                        startActivity(intent);
                    }
                });

        rvShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvShifts.setAdapter(adapter);

        loadShifts();
    }

    private void openSession(Shift shift) {
        Intent intent = new Intent(requireContext(), SessionManagementActivity.class);
        intent.putExtra(SessionManagementActivity.EXTRA_SHIFT_ID, shift.getShiftId());
        intent.putExtra(SessionManagementActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(SessionManagementActivity.EXTRA_CLASS_NAME, className);
        sessionLauncher.launch(intent);
    }

    // The list updates itself in real time: getClassShifts() is backed by a Firestore
    // snapshot listener, so opening/closing a session (which rewrites the shift's
    // status/attendanceOpened) is reflected here immediately — no manual refresh needed.
    private void loadShifts() {
        if (classId == null) return;
        repo.getClassShifts(classId).observe(getViewLifecycleOwner(), shifts -> {
            shiftList.clear();
            shiftList.addAll(shifts);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(shifts.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
