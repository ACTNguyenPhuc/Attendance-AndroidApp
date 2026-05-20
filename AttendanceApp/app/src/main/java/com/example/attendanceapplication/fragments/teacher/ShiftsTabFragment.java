package com.example.attendanceapplication.fragments.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.adapters.ShiftListAdapter;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;

import java.util.*;

public class ShiftsTabFragment extends Fragment {

    private RecyclerView rvShifts;
    private TextView tvEmpty;
    private ShiftListAdapter adapter;
    private List<Shift> shiftList = new ArrayList<>();
    private String classId, className;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

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

        adapter = new ShiftListAdapter(shiftList, classId, className, shift -> {
            Intent intent = new Intent(requireContext(), SessionManagementActivity.class);
            intent.putExtra(SessionManagementActivity.EXTRA_SHIFT_ID, shift.getShiftId());
            intent.putExtra(SessionManagementActivity.EXTRA_CLASS_ID, classId);
            intent.putExtra(SessionManagementActivity.EXTRA_CLASS_NAME, className);
            startActivity(intent);
        });

        rvShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvShifts.setAdapter(adapter);

        loadShifts();
    }

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
