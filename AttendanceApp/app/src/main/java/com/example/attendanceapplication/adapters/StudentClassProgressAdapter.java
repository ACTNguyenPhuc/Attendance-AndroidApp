package com.example.attendanceapplication.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thẻ lớp học (cuộn ngang) ở trang chủ sinh viên, kèm tỉ lệ điểm danh.
 * Tỉ lệ = -1 nghĩa là chưa có buổi nào kết thúc -> hiển thị "--".
 */
public class StudentClassProgressAdapter
        extends RecyclerView.Adapter<StudentClassProgressAdapter.VH> {

    public interface OnClassClickListener {
        void onClick(ClassModel cls);
    }

    private final List<ClassModel> classes;
    private final Map<String, Integer> rateByClassId = new HashMap<>();
    private final OnClassClickListener listener;

    public StudentClassProgressAdapter(List<ClassModel> classes, OnClassClickListener listener) {
        this.classes = classes;
        this.listener = listener;
    }

    public void setRate(String classId, int ratePercent) {
        if (classId != null) {
            rateByClassId.put(classId, ratePercent);
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_class_progress, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ClassModel c = classes.get(position);
        h.tvClassName.setText(c.getClassName());
        h.tvClassCode.setText(c.getClassId());

        Integer rate = rateByClassId.get(c.getClassId());
        int color = colorForRate(h, rate);

        if (rate == null || rate < 0) {
            h.tvRate.setText("--");
            h.progress.setProgress(0);
        } else {
            h.tvRate.setText(rate + "%");
            h.progress.setProgress(Math.max(0, Math.min(100, rate)));
        }
        h.tvRate.setTextColor(color);
        h.progress.setProgressTintList(ColorStateList.valueOf(color));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    private int colorForRate(VH h, Integer rate) {
        int res;
        if (rate == null || rate < 0) res = R.color.text_secondary;
        else if (rate >= 80) res = R.color.accent_green;
        else if (rate >= 50) res = R.color.warning_yellow;
        else res = R.color.error_red;
        return ContextCompat.getColor(h.itemView.getContext(), res);
    }

    @Override
    public int getItemCount() {
        return classes.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvClassName, tvClassCode, tvRate;
        ProgressBar progress;

        VH(@NonNull View itemView) {
            super(itemView);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvClassCode = itemView.findViewById(R.id.tv_class_code);
            tvRate = itemView.findViewById(R.id.tv_rate);
            progress = itemView.findViewById(R.id.progress_rate);
        }
    }
}
