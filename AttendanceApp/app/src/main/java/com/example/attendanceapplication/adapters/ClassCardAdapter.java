package com.example.attendanceapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
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

        holder.card.setOnClickListener(v -> listener.onClick(classModel));
    }

    @Override
    public int getItemCount() { return classList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvClassName, tvClassId, tvSchedule, tvRoom;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card        = itemView.findViewById(R.id.card_class);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvClassId   = itemView.findViewById(R.id.tv_class_id);
            tvSchedule  = itemView.findViewById(R.id.tv_schedule);
            tvRoom      = itemView.findViewById(R.id.tv_room);
        }
    }
}
