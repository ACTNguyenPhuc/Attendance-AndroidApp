package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Shift;

import java.util.List;

public class ShiftAgendaAdapter extends RecyclerView.Adapter<ShiftAgendaAdapter.ViewHolder> {

    public interface OnShiftClickListener {
        void onClick(Shift shift);
    }

    public interface OnShiftActionListener {
        void onAction(Shift shift);
    }

    private final List<Shift> shiftList;
    private final OnShiftClickListener listener;
    private final OnShiftActionListener actionListener;

    public ShiftAgendaAdapter(List<Shift> shiftList,
                              OnShiftClickListener listener,
                              OnShiftActionListener actionListener) {
        this.shiftList = shiftList;
        this.listener = listener;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shift_agenda, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shift shift = shiftList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvClassName.setText(shift.getClassName() != null ? shift.getClassName() : shift.getTitle());
        holder.tvTimeStart.setText(shift.getStartAt() != null ? shift.getStartAt() : "");
        holder.tvTimeEnd.setText(shift.getEndAt() != null ? shift.getEndAt() : "");
        holder.tvRoom.setText(shift.getRoom() != null ? "Phòng: " + shift.getRoom() : "");
        holder.tvTeacher.setText(shift.getTeacherName() != null ? "Giảng viên: " + shift.getTeacherName() : "");
        // "Sắp diễn ra" only shows when the shift is within 1 day from today.
        boolean hideUpcoming = Shift.STATUS_UPCOMING.equals(shift.getStatus())
                && !com.example.attendanceapplication.utils.AttendanceUtils
                        .shouldShowUpcomingBadge(shift.getDate());
        if (hideUpcoming) {
            holder.tvStatus.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(getStatusText(shift.getStatus()));
            holder.tvStatus.setTextColor(getStatusColor(ctx, shift.getStatus()));
        }

        int statusColor = getStatusColor(ctx, shift.getStatus());
        holder.viewLine.setBackgroundColor(statusColor);
        holder.viewDot.setBackgroundTintList(ColorStateList.valueOf(statusColor));

        if (shift.isAttendanceOpened()) {
            holder.btnAttendNow.setVisibility(View.VISIBLE);
            holder.btnAttendNow.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onAction(shift);
            });
        } else {
            holder.btnAttendNow.setVisibility(View.GONE);
        }

        holder.card.setOnClickListener(v -> {
            if (shift.isAttendanceOpened() && actionListener != null) {
                actionListener.onAction(shift);
                return;
            }
            if (listener != null) listener.onClick(shift);
        });
    }

    private String getStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case Shift.STATUS_UPCOMING: return "Sắp diễn ra";
            case Shift.STATUS_ONGOING: return "Đang diễn ra";
            case Shift.STATUS_COMPLETED: return "Đã kết thúc";
            case Shift.STATUS_CANCELLED: return "Đã hủy";
            default: return status;
        }
    }

    private int getStatusColor(Context ctx, String status) {
        if (Shift.STATUS_ONGOING.equals(status))
            return ContextCompat.getColor(ctx, R.color.accent_green);
        if (Shift.STATUS_COMPLETED.equals(status))
            return ContextCompat.getColor(ctx, R.color.text_secondary);
        return ContextCompat.getColor(ctx, R.color.primary_light);
    }

    @Override
    public int getItemCount() { return shiftList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvClassName, tvTimeStart, tvTimeEnd, tvRoom, tvTeacher, tvStatus;
        View viewDot, viewLine;
        Button btnAttendNow;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card        = itemView.findViewById(R.id.card_shift);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvTimeStart = itemView.findViewById(R.id.tv_time_start);
            tvTimeEnd   = itemView.findViewById(R.id.tv_time_end);
            tvRoom      = itemView.findViewById(R.id.tv_room);
            tvTeacher   = itemView.findViewById(R.id.tv_teacher);
            tvStatus    = itemView.findViewById(R.id.tv_status);
            viewDot     = itemView.findViewById(R.id.view_dot);
            viewLine    = itemView.findViewById(R.id.view_line);
            btnAttendNow = itemView.findViewById(R.id.btn_attend_now);
        }
    }
}
