package com.example.attendanceapplication.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thẻ lớp học (danh sách dọc) của sinh viên: tên lớp, mã lớp, giáo viên,
 * lịch học, phòng, sĩ số và tỉ lệ điểm danh. Tỉ lệ = -1 hoặc chưa có dữ liệu
 * -> hiển thị "--".
 */
public class StudentClassCardAdapter
        extends RecyclerView.Adapter<StudentClassCardAdapter.ViewHolder> {

    public interface OnClassClickListener {
        void onClick(ClassModel classModel);
    }

    private final List<ClassModel> classList;
    private final OnClassClickListener listener;
    private final Map<String, Integer> rateByClassId = new HashMap<>();

    public StudentClassCardAdapter(List<ClassModel> classList, OnClassClickListener listener) {
        this.classList = classList;
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_class_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassModel classModel = classList.get(position);
        android.content.Context ctx = holder.itemView.getContext();

        holder.tvClassName.setText(classModel.getClassName());
        holder.tvClassId.setText(classModel.getClassId());
        holder.tvTeacher.setText(classModel.getTeacherName() != null
                && !classModel.getTeacherName().isEmpty()
                ? "GV: " + classModel.getTeacherName() : "Đang tải...");
        holder.tvSchedule.setText(scheduleDisplay(classModel));
        holder.tvRoom.setText(classModel.getRoom() != null && !classModel.getRoom().isEmpty()
                ? classModel.getRoom() : "Chưa có phòng");

        // Dải màu trái + mã lớp đổi màu theo vị trí
        int accent = accentColor(ctx, position);
        holder.viewAccent.setBackgroundColor(accent);
        holder.tvClassId.setBackgroundTintList(ColorStateList.valueOf(accent));

        // Tỉ lệ điểm danh
        Integer rate = rateByClassId.get(classModel.getClassId());
        int rateColor = colorForRate(ctx, rate);
        if (rate == null || rate < 0) {
            holder.tvRate.setText("--");
            holder.progress.setProgress(0);
        } else {
            holder.tvRate.setText(rate + "%");
            holder.progress.setProgress(Math.max(0, Math.min(100, rate)));
        }
        holder.tvRate.setTextColor(rateColor);
        holder.progress.setProgressTintList(ColorStateList.valueOf(rateColor));

        holder.card.setOnClickListener(v -> listener.onClick(classModel));
    }

    @Override
    public int getItemCount() { return classList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        View viewAccent;
        TextView tvClassName, tvClassId, tvTeacher, tvSchedule, tvRoom, tvRate;
        ProgressBar progress;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card           = itemView.findViewById(R.id.card_class);
            viewAccent     = itemView.findViewById(R.id.view_accent);
            tvClassName    = itemView.findViewById(R.id.tv_class_name);
            tvClassId      = itemView.findViewById(R.id.tv_class_id);
            tvTeacher      = itemView.findViewById(R.id.tv_teacher);
            tvSchedule     = itemView.findViewById(R.id.tv_schedule);
            tvRoom         = itemView.findViewById(R.id.tv_room);
            tvRate         = itemView.findViewById(R.id.tv_rate);
            progress       = itemView.findViewById(R.id.progress_rate);
        }
    }

    /** "Thứ 2, Thứ 4, 18:00-20:00" theo quy ước VN (2=T2 … 8=CN). */
    private String scheduleDisplay(ClassModel c) {
        StringBuilder sb = new StringBuilder();
        List<Integer> schedule = c.getSchedule();
        if (schedule != null && !schedule.isEmpty()) {
            for (int i = 0; i < schedule.size(); i++) {
                if (i > 0) sb.append(", ");
                int d = schedule.get(i);
                sb.append(d == 8 ? "Chủ nhật" : "Thứ " + d);
            }
        }
        if (c.getStartAt() != null && c.getEndAt() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.getStartAt()).append("-").append(c.getEndAt());
        }
        return sb.toString();
    }

    private int accentColor(android.content.Context ctx, int position) {
        int[] colors = new int[] {
                ContextCompat.getColor(ctx, R.color.primary_blue),
                ContextCompat.getColor(ctx, R.color.accent_green),
                ContextCompat.getColor(ctx, R.color.warning_yellow)
        };
        return colors[position % colors.length];
    }

    private int colorForRate(android.content.Context ctx, Integer rate) {
        int res;
        if (rate == null || rate < 0) res = R.color.text_secondary;
        else if (rate >= 80) res = R.color.accent_green;
        else if (rate >= 50) res = R.color.warning_yellow;
        else res = R.color.error_red;
        return ContextCompat.getColor(ctx, res);
    }
}
