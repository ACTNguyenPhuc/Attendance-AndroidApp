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
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.*;

import java.util.*;

public class StatsTabFragment extends Fragment {

    private PieChart pieChart;
    private TextView tvTotalShifts, tvAvgRate, tvTotalStudents;
    private String classId;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

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
        tvTotalShifts  = view.findViewById(R.id.tv_total_shifts);
        tvAvgRate      = view.findViewById(R.id.tv_avg_rate);
        tvTotalStudents = view.findViewById(R.id.tv_total_students);

        setupPieChart();
        loadStats();
    }

    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(45f);
        pieChart.setCenterText("Điểm danh");
        pieChart.setCenterTextSize(14f);
        pieChart.getLegend().setEnabled(true);
    }

    private void loadStats() {
        if (classId == null) return;
        // Load shifts count
        repo.getClassShifts(classId).observe(getViewLifecycleOwner(), shifts -> {
            tvTotalShifts.setText("Tổng buổi: " + shifts.size());
        });

        // Load students count
        repo.getClassStudents(classId,
                students -> requireActivity().runOnUiThread(() ->
                        tvTotalStudents.setText("Sinh viên: " + students.size())),
                e -> {}
        );

        // Build pie chart with dummy present/absent data
        // In production, aggregate from attendances collection
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(75f, "Đã điểm danh"));
        entries.add(new PieEntry(25f, "Vắng mặt"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{
                Color.parseColor("#2E7D32"),
                Color.parseColor("#C62828")
        });
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate();
        tvAvgRate.setText("Tỉ lệ TB: 75%");
    }
}
