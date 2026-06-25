package com.example.attendanceapplication.fragments.student;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.ScanAttendanceActivity;
import com.example.attendanceapplication.activities.ShiftDetailActivity;
import com.example.attendanceapplication.adapters.ShiftAgendaAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;

import java.util.*;

public class StudentCalendarFragment extends Fragment {

    private MaterialCalendarView calendarView;
    private RecyclerView rvAgenda;
    private TextView tvSelectedDate;
    private LinearLayout emptyState;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final List<Shift> agendaShifts = new ArrayList<>();
    private final List<String> enrolledClassIds = new ArrayList<>();
    private final Set<CalendarDay> shiftDays = new HashSet<>();
    private ShiftAgendaAdapter agendaAdapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView   = view.findViewById(R.id.calendar_view);
        rvAgenda       = view.findViewById(R.id.rv_agenda);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        emptyState     = view.findViewById(R.id.empty_state);

        hideCalendarTopBar();

        agendaAdapter = new ShiftAgendaAdapter(agendaShifts,
                this::openShiftDetail,
                shift -> openScan()
        );
        rvAgenda.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAgenda.setAdapter(agendaAdapter);
        setAgendaEmpty(true);

        CalendarDay today = CalendarDay.today();
        calendarView.setSelectedDate(today);
        applyDecorators();

        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            updateSelectedHeader(date);
            loadShiftsForDate(date);
        });

        calendarView.setOnMonthChangedListener((widget, date) -> refreshMonthDots(date));

        loadEnrolledClasses();
        updateSelectedHeader(today);
        loadShiftsForDate(today);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (calendarView == null || enrolledClassIds.isEmpty()) return;
        refreshMonthDots(calendarView.getCurrentDate());
        CalendarDay selected = calendarView.getSelectedDate();
        if (selected != null) loadShiftsForDate(selected);
    }

    private void loadEnrolledClasses() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            enrolledClassIds.clear();
            for (ClassModel c : classes) enrolledClassIds.add(c.getClassId());
            refreshMonthDots(calendarView.getCurrentDate());
            CalendarDay selected = calendarView.getSelectedDate();
            if (selected != null) loadShiftsForDate(selected);
        });
    }

    private void loadShiftsForDate(CalendarDay day) {
        if (day == null) return;
        if (enrolledClassIds.isEmpty()) {
            setAgendaEmpty(true);
            return;
        }
        String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                day.getYear(), day.getMonth(), day.getDay());
        repo.getShiftsByDate(enrolledClassIds, dateStr, shifts -> {
            agendaShifts.clear();
            agendaShifts.addAll(shifts);
            agendaAdapter.notifyDataSetChanged();
            setAgendaEmpty(shifts.isEmpty());
        });
    }

    private void refreshMonthDots(CalendarDay month) {
        if (month == null || enrolledClassIds.isEmpty()) {
            shiftDays.clear();
            applyDecorators();
            return;
        }
        repo.getShiftsForClasses(enrolledClassIds, shifts -> {
            if (!isAdded() || getContext() == null) return;
            shiftDays.clear();
            for (Shift s : shifts) {
                CalendarDay day = parseShiftDay(s);
                if (day != null && day.getYear() == month.getYear()
                        && day.getMonth() == month.getMonth()) {
                    shiftDays.add(day);
                }
            }
            applyDecorators();
        });
    }

    private CalendarDay parseShiftDay(Shift shift) {
        if (shift == null || shift.getDate() == null) return null;
        try {
            String[] parts = shift.getDate().split("-");
            return CalendarDay.from(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }

    private void updateSelectedHeader(CalendarDay day) {
        if (day == null) return;
        Calendar cal = Calendar.getInstance();
        cal.set(day.getYear(), day.getMonth() - 1, day.getDay());
        String[] week = {"Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};
        String label = week[cal.get(Calendar.DAY_OF_WEEK) - 1] +
                String.format(Locale.US, ", ngày %02d/%02d/%04d",
                        day.getDay(), day.getMonth(), day.getYear());
        tvSelectedDate.setText(label);
    }

    private void openShiftDetail(Shift shift) {
        if (shift == null) return;
        Intent i = new Intent(requireContext(), ShiftDetailActivity.class);
        i.putExtra(ShiftDetailActivity.EXTRA_SHIFT_ID, shift.getShiftId());
        i.putExtra(ShiftDetailActivity.EXTRA_CLASS_ID, shift.getClassId());
        i.putExtra(ShiftDetailActivity.EXTRA_CLASS_NAME, shift.getClassName());
        startActivity(i);
    }

    private void openScan() {
        startActivity(new Intent(requireContext(), ScanAttendanceActivity.class));
    }

    private void setAgendaEmpty(boolean isEmpty) {
        rvAgenda.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void applyDecorators() {
        calendarView.removeDecorators();
        calendarView.addDecorator(new TodayDecorator(requireContext()));
        calendarView.addDecorator(new DotDecorator(shiftDays,
                ContextCompat.getColor(requireContext(), R.color.error_red)));
    }

    private void hideCalendarTopBar() {
        int topbarId = getResources().getIdentifier(
                "topbar", "id", "com.prolificinteractive.materialcalendarview");
        if (topbarId == 0) return;
        View topbar = calendarView.findViewById(topbarId);
        if (topbar != null) topbar.setVisibility(View.GONE);
    }

    static class DotDecorator implements DayViewDecorator {
        private final HashSet<CalendarDay> dates;
        private final int color;

        DotDecorator(Collection<CalendarDay> dates, int color) {
            this.dates = new HashSet<>(dates);
            this.color = color;
        }

        @Override
        public boolean shouldDecorate(CalendarDay day) {
            return dates.contains(day);
        }

        @Override
        public void decorate(DayViewFacade view) {
            view.addSpan(new DotSpan(6f, color));
        }
    }

    static class TodayDecorator implements DayViewDecorator {
        private final CalendarDay today = CalendarDay.today();
        private final int color;
        private final int textColor;
        private final GradientDrawable background;

        TodayDecorator(android.content.Context ctx) {
            color = ContextCompat.getColor(ctx, R.color.primary_light_transparent);
            textColor = ContextCompat.getColor(ctx, R.color.black);
            background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(color);
        }

        @Override
        public boolean shouldDecorate(CalendarDay day) {
            return today.equals(day);
        }

        @Override
        public void decorate(DayViewFacade view) {
            view.setBackgroundDrawable(background);
            view.addSpan(new ForegroundColorSpan(textColor));
        }
    }
}
