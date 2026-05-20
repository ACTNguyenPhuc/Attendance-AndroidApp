package com.example.attendanceapplication.fragments.student;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.*;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.ShiftAgendaAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.prolificinteractive.materialcalendarview.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class StudentCalendarFragment extends Fragment {

    private MaterialCalendarView calendarView;
    private RecyclerView rvAgenda;
    private TextView tvSelectedDate;
    private ShiftAgendaAdapter agendaAdapter;
    private List<Shift> agendaShifts = new ArrayList<>();
    private List<String> enrolledClassIds = new ArrayList<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView  = view.findViewById(R.id.calendar_view);
        rvAgenda      = view.findViewById(R.id.rv_agenda);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);

        agendaAdapter = new ShiftAgendaAdapter(agendaShifts, shift -> {
            // Navigate to shift detail
        });
        rvAgenda.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAgenda.setAdapter(agendaAdapter);

        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                    date.getYear(), date.getMonth(), date.getDay());
            loadShiftsForDate(dateStr);
        });

        loadEnrolledClasses();
    }

    private void loadEnrolledClasses() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            enrolledClassIds.clear();
            for (ClassModel c : classes) enrolledClassIds.add(c.getClassId());
        });
    }

    private void loadShiftsForDate(String dateStr) {
        tvSelectedDate.setText(dateStr);
        if (enrolledClassIds.isEmpty()) return;
        repo.getShiftsByDate(enrolledClassIds, dateStr, shifts -> {
            agendaShifts.clear();
            agendaShifts.addAll(shifts);
            agendaAdapter.notifyDataSetChanged();
        });
    }
}
