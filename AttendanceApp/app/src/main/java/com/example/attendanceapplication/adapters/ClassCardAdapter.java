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
import com.example.attendanceapplication.utils.ClassCardUi;

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
        ClassCardUi.bindAvatar(holder.tvAvatar, classModel.getClassName(), position);
        ClassCardUi.bindScheduleChips(holder.cgSchedule, classModel);
        String room = classModel.getRoom();
        boolean hasRoom = room != null && !room.trim().isEmpty();
        holder.layoutRoom.setVisibility(hasRoom ? View.VISIBLE : View.GONE);
        holder.tvRoom.setText(hasRoom ? room : "");
        holder.tvStudentCount.setText("Sinh viên: " + classModel.getStudentCount());

        holder.card.setOnClickListener(v -> listener.onClick(classModel));
    }

    @Override
    public int getItemCount() { return classList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        View layoutRoom;
        ViewGroup cgSchedule;
        TextView tvAvatar, tvClassName, tvClassId, tvRoom, tvStudentCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card        = itemView.findViewById(R.id.card_class);
            tvAvatar    = itemView.findViewById(R.id.tv_avatar);
            layoutRoom  = itemView.findViewById(R.id.layout_room);
            cgSchedule  = itemView.findViewById(R.id.cg_schedule);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvClassId   = itemView.findViewById(R.id.tv_class_id);
            tvRoom      = itemView.findViewById(R.id.tv_room);
            tvStudentCount = itemView.findViewById(R.id.tv_student_count);
        }
    }

}
