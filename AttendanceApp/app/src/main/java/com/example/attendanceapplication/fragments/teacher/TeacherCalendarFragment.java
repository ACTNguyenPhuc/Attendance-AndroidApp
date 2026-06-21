package com.example.attendanceapplication.fragments.teacher;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.style.ForegroundColorSpan;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.activities.ShiftAttendanceListActivity;
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

import java.text.SimpleDateFormat;
import java.util.*;

public class TeacherCalendarFragment extends Fragment {
    private MaterialCalendarView calendarView;
    private RecyclerView rvAgenda;
    private TextView tvSelectedDate;

    private LinearLayout emptyState;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final List<Shift> agendaShifts = new ArrayList<>();
    private final List<String> classIds = new ArrayList<>();
    private final Set<CalendarDay> shiftDays = new HashSet<>();
    private ShiftAgendaAdapter agendaAdapter;

    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MM/yyyy", Locale.US);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendar_view);
        rvAgenda = view.findViewById(R.id.rv_agenda);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);

        emptyState = view.findViewById(R.id.empty_state);

        hideCalendarTopBar();

        agendaAdapter = new ShiftAgendaAdapter(agendaShifts,
                shift -> {
                    if (Shift.STATUS_COMPLETED.equals(shift.getStatus())) {
                        openAttendanceList(shift);
                    }
                },
                shift -> openSessionManagement(shift)
        );
        rvAgenda.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAgenda.setAdapter(agendaAdapter);
        setAgendaEmpty(true);

        CalendarDay today = CalendarDay.today();
        calendarView.setSelectedDate(today);
        updateMonthTitle(today);
        applyDecorators();

        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            updateSelectedHeader(date);
            loadShiftsForDate(date);
        });

        calendarView.setOnMonthChangedListener((widget, date) -> {
            updateMonthTitle(date);
            refreshMonthDots(date);
        });

        loadTeacherClasses();
        updateSelectedHeader(today);
        loadShiftsForDate(today);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (calendarView == null || classIds.isEmpty()) return;
        refreshMonthDots(calendarView.getCurrentDate());
        CalendarDay selected = calendarView.getSelectedDate();
        if (selected != null) loadShiftsForDate(selected);
    }

    private void loadTeacherClasses() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        repo.getTeacherClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            classIds.clear();
            for (ClassModel c : classes) classIds.add(c.getClassId());
            refreshMonthDots(calendarView.getCurrentDate());
            CalendarDay selected = calendarView.getSelectedDate();
            if (selected != null) {
                loadShiftsForDate(selected);
            }
        });
    }

    private void loadShiftsForDate(CalendarDay day) {
        if (day == null) return;
        String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                day.getYear(), day.getMonth(), day.getDay());
        if (classIds.isEmpty()) {
            setAgendaEmpty(true);
            return;
        }
        repo.getShiftsByDate(classIds, dateStr, shifts -> {
            agendaShifts.clear();
            agendaShifts.addAll(shifts);
            agendaAdapter.notifyDataSetChanged();
            setAgendaEmpty(shifts.isEmpty());
        });
    }

    private void refreshMonthDots(CalendarDay month) {
        if (month == null || classIds.isEmpty()) {
            shiftDays.clear();
            applyDecorators();
            return;
        }

        repo.getShiftsForClasses(classIds, shifts -> {
            shiftDays.clear();
            for (Shift s : shifts) {
                CalendarDay day = parseShiftDay(s);
                if (day != null && day.getYear() == month.getYear() && day.getMonth() == month.getMonth()) {
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
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);
            return CalendarDay.from(y, m, d);
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

    private void updateMonthTitle(CalendarDay day) {
        Calendar cal = Calendar.getInstance();
        cal.set(day.getYear(), day.getMonth() - 1, 1);

    }

    private void changeMonth(int diff) {
        CalendarDay current = calendarView.getCurrentDate();
        Calendar cal = Calendar.getInstance();
        cal.set(current.getYear(), current.getMonth() - 1, 1);
        cal.add(Calendar.MONTH, diff);
        CalendarDay target = CalendarDay.from(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            1
        );
        calendarView.setCurrentDate(target, true);
    }

    private void openSessionManagement(Shift shift) {
        if (shift == null) return;
        Intent intent = new Intent(requireContext(), SessionManagementActivity.class);
        intent.putExtra(SessionManagementActivity.EXTRA_SHIFT_ID, shift.getShiftId());
        intent.putExtra(SessionManagementActivity.EXTRA_CLASS_ID, shift.getClassId());
        intent.putExtra(SessionManagementActivity.EXTRA_CLASS_NAME, shift.getClassName());
        startActivity(intent);
    }

    private void openAttendanceList(Shift shift) {
        if (shift == null) return;
        Intent intent = new Intent(requireContext(), ShiftAttendanceListActivity.class);
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_ID, shift.getShiftId());
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_ID, shift.getClassId());
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_NAME, shift.getClassName());
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TITLE,
                shift.getTitle() != null ? shift.getTitle() : shift.getClassName());
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TIME,
                shift.getDayOfWeekDisplay() + "  " + shift.getStartAt() + " - " + shift.getEndAt());
        startActivity(intent);
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
