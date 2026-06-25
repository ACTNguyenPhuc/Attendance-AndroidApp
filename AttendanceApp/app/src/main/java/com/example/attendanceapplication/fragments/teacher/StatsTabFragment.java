package com.example.attendanceapplication.fragments.teacher;

import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.*;

public class StatsTabFragment extends Fragment {

    private PieChart pieChart;
    private BarChart barChart;
    private TextView tvTotalShifts, tvPastShifts, tvAvgRate, tvTotalStudents;
    private String classId;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    private final List<Shift> currentShifts = new ArrayList<>();
    private final List<Attendance> currentAttendances = new ArrayList<>();
    private final List<User> currentStudents = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c,
                             @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_stats_tab, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) classId = getArguments().getString("classId");

        pieChart       = view.findViewById(R.id.pie_chart);
        barChart       = view.findViewById(R.id.bar_chart);
        tvTotalShifts  = view.findViewById(R.id.tv_total_shifts);
        tvPastShifts   = view.findViewById(R.id.tv_past_shifts);
        tvAvgRate      = view.findViewById(R.id.tv_avg_rate);
        tvTotalStudents = view.findViewById(R.id.tv_total_students);

        setupPieChart();
        setupBarChart();
        loadStats();
    }

    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(45f);
        pieChart.setCenterText("Phân loại");
        pieChart.setCenterTextSize(14f);
        pieChart.getLegend().setEnabled(true);
    }

    private void setupBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDragEnabled(true);
        barChart.setScaleEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setFitBars(true);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setAxisMaximum(100f);
        barChart.getAxisLeft().setGranularity(10f);
        barChart.getLegend().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
    }

    private void loadStats() {
        if (classId == null) return;
        // Shifts are realtime (snapshot listener) — observe once.
        repo.getClassShifts(classId).observe(getViewLifecycleOwner(), shifts -> {
            currentShifts.clear();
            currentShifts.addAll(shifts);
            tvTotalShifts.setText(String.valueOf(shifts.size()));
            recomputeStats();
        });
        loadOneShotStats();
    }

    /**
     * Re-fetches the one-shot (non-realtime) data: students and attendances.
     * Called on every onResume so the screen refreshes after an action
     * (e.g. closing a session) without needing a manual reload.
     */
    private void loadOneShotStats() {
        if (classId == null) return;
        repo.getClassStudents(classId,
                students -> {
                    if (!isAdded() || getActivity() == null) return;
                    requireActivity().runOnUiThread(() -> {
                        currentStudents.clear();
                        currentStudents.addAll(students);
                        tvTotalStudents.setText(String.valueOf(students.size()));
                        recomputeStats();
                    });
                },
                e -> {}
        );

        repo.getClassAttendances(classId,
                attendances -> {
                    if (!isAdded() || getActivity() == null) return;
                    requireActivity().runOnUiThread(() -> {
                        currentAttendances.clear();
                        currentAttendances.addAll(attendances);
                        recomputeStats();
                    });
                },
                e -> {}
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOneShotStats();
    }

    private void recomputeStats() {
        if (currentShifts.isEmpty() || currentStudents.isEmpty()) {
            updateBarChart(Collections.emptyList(), Collections.emptyList());
            updatePieChart(0, 0, 0);
            tvPastShifts.setText("0");
            tvAvgRate.setText("0%");
            return;
        }

        List<Shift> sortedShifts = new ArrayList<>(currentShifts);
        sortedShifts.sort(Comparator.comparing(Shift::getDate, Comparator.nullsLast(String::compareTo)));

        Map<String, Integer> presentByShift = new HashMap<>();
        Map<String, Integer> presentByStudent = new HashMap<>();
        for (Attendance a : currentAttendances) {
            if (a == null) continue;
            if (!isPresent(a)) continue;
            if (a.getShiftId() != null) {
                presentByShift.put(a.getShiftId(), presentByShift.getOrDefault(a.getShiftId(), 0) + 1);
            }
            if (a.getStudentId() != null) {
                presentByStudent.put(a.getStudentId(), presentByStudent.getOrDefault(a.getStudentId(), 0) + 1);
            }
        }

        List<BarEntry> barEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int totalStudents = currentStudents.size();

        // "Buổi đã qua" = buổi đã điểm danh (đã đóng phiên, status = completed).
        // Tỉ lệ điểm danh chỉ tính trung bình trên các buổi đã qua này.
        int pastShifts = 0;
        float pastRateSum = 0f;

        for (int i = 0; i < sortedShifts.size(); i++) {
            Shift s = sortedShifts.get(i);
            String shiftId = s.getShiftId();
            int present = shiftId == null ? 0 : presentByShift.getOrDefault(shiftId, 0);
            float rate = totalStudents == 0 ? 0f : (present * 100f / totalStudents);
            barEntries.add(new BarEntry(i, rate));
            labels.add("Buổi " + (i + 1));

            if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
                pastShifts++;
                pastRateSum += rate;
            }
        }

        float avgRate = pastShifts == 0 ? 0f : (pastRateSum / pastShifts);
        tvPastShifts.setText(String.valueOf(pastShifts));
        tvAvgRate.setText(Math.round(avgRate) + "%");

        updateBarChart(barEntries, labels);

        // Phân loại sinh viên theo tỉ lệ chuyên cần trên số buổi đã qua.
        int excellent = 0;
        int good = 0;
        int weak = 0;

        for (User u : currentStudents) {
            if (u == null || u.getUid() == null) continue;
            int attended = presentByStudent.getOrDefault(u.getUid(), 0);
            float rate = pastShifts == 0 ? 0f : (attended * 1f / pastShifts);
            if (rate >= 0.8f) excellent++;
            else if (rate >= 0.5f) good++;
            else weak++;
        }

        updatePieChart(excellent, good, weak);
    }

    private void updateBarChart(List<BarEntry> entries, List<String> labels) {
        if (entries.isEmpty()) {
            barChart.clear();
            barChart.setNoDataText("Chưa có dữ liệu");
            barChart.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(Color.parseColor("#1E88E5"));
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        barChart.setData(data);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.setVisibleXRangeMaximum(5f);
        barChart.invalidate();
    }

    private void updatePieChart(int excellent, int good, int weak) {
        // Chỉ thêm các nhóm có giá trị > 0 để nhãn không đè lên nhau khi = 0.
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (excellent > 0) {
            entries.add(new PieEntry(excellent, "Xuất sắc (>80%)"));
            colors.add(Color.parseColor("#2E7D32"));
        }
        if (good > 0) {
            entries.add(new PieEntry(good, "Khá (50-80%)"));
            colors.add(Color.parseColor("#F57F17"));
        }
        if (weak > 0) {
            entries.add(new PieEntry(weak, "Yếu (<50%)"));
            colors.add(Color.parseColor("#C62828"));
        }

        if (entries.isEmpty()) {
            pieChart.clear();
            pieChart.setNoDataText("Chưa có dữ liệu");
            pieChart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate();
    }

    private boolean isPresent(Attendance a) {
        String status = a.getStatus();
        return Attendance.STATUS_PRESENT.equals(status) || Attendance.STATUS_LATE.equals(status);
    }
}
