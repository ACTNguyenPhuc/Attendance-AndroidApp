package com.example.attendanceapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;

import java.util.List;

public class ClassCardAdapter extends RecyclerView.Adapter<ClassCardAdapter.ViewHolder> {

    public interface OnClassClickListener {
        void onClick(ClassModel classModel);
    }

    private final List<ClassModel> classList;
    private final OnClassClickListener listener;

    public ClassCardAdapter(List<ClassModel> classList, OnClassClickListener listener) {
        this.classList = classList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassModel classModel = classList.get(position);
        holder.tvClassName.setText(classModel.getClassName());
        holder.tvClassId.setText(classModel.getClassId());
        holder.tvSchedule.setText(classModel.getScheduleDisplay() +
                "  " + classModel.getStartAt() + "-" + classModel.getEndAt());
        holder.tvRoom.setText(classModel.getRoom() != null ? classModel.getRoom() : "");
        holder.tvStudentCount.setText("Sinh vien: " + classModel.getStudentCount());
        holder.viewHeader.setBackgroundColor(getHeaderColor(holder.itemView.getContext(), position));

        holder.card.setOnClickListener(v -> listener.onClick(classModel));
    }

    @Override
    public int getItemCount() { return classList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        View viewHeader;
        TextView tvClassName, tvClassId, tvSchedule, tvRoom, tvStudentCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card        = itemView.findViewById(R.id.card_class);
            viewHeader  = itemView.findViewById(R.id.view_header);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvClassId   = itemView.findViewById(R.id.tv_class_id);
            tvSchedule  = itemView.findViewById(R.id.tv_schedule);
            tvRoom      = itemView.findViewById(R.id.tv_room);
            tvStudentCount = itemView.findViewById(R.id.tv_student_count);
        }
    }

    private int getHeaderColor(android.content.Context ctx, int position) {
        int[] colors = new int[] {
                ContextCompat.getColor(ctx, R.color.accent_green),
                ContextCompat.getColor(ctx, R.color.accent_yellow),
                ContextCompat.getColor(ctx, R.color.primary_blue)
        };
        int index = position % colors.length;
        return colors[index];
    }
}
