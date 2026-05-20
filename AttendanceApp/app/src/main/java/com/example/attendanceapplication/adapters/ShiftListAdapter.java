package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.models.Shift;

import java.util.List;

public class ShiftListAdapter extends RecyclerView.Adapter<ShiftListAdapter.ViewHolder> {

    public interface OnOpenAttendanceListener {
        void onOpen(Shift shift);
    }

    private final List<Shift> shiftList;
    private final String classId;
    private final String className;
    private final OnOpenAttendanceListener listener;

    public ShiftListAdapter(List<Shift> shiftList, String classId, String className,
                            OnOpenAttendanceListener listener) {
        this.shiftList = shiftList;
        this.classId   = classId;
        this.className = className;
        this.listener  = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shift_teacher, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shift shift = shiftList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvDate.setText(shift.getDate());
        holder.tvDay.setText(shift.getDayOfWeekDisplay());
        holder.tvTime.setText(shift.getStartAt() + " - " + shift.getEndAt());

        // Status badge
        String status = shift.getStatus();
        holder.tvStatus.setText(getStatusText(status));
        holder.tvStatus.setBackgroundColor(getStatusColor(ctx, status));

        // Attendance QR icon
        if (shift.isAttendanceOpened()) {
            holder.tvAttIcon.setVisibility(View.VISIBLE);
            holder.btnOpenAtt.setVisibility(View.GONE);
        } else if (Shift.STATUS_ONGOING.equals(status) || Shift.STATUS_UPCOMING.equals(status)) {
            holder.btnOpenAtt.setVisibility(View.VISIBLE);
            holder.tvAttIcon.setVisibility(View.GONE);
            holder.btnOpenAtt.setOnClickListener(v -> listener.onOpen(shift));
        } else {
            holder.btnOpenAtt.setVisibility(View.GONE);
            holder.tvAttIcon.setVisibility(View.GONE);
        }
    }

    private String getStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case Shift.STATUS_UPCOMING:  return "Sắp diễn ra";
            case Shift.STATUS_ONGOING:   return "Đang diễn ra";
            case Shift.STATUS_COMPLETED: return "Đã kết thúc";
            case Shift.STATUS_CANCELLED: return "Đã hủy";
            default: return status;
        }
    }

    private int getStatusColor(Context ctx, String status) {
        if (Shift.STATUS_ONGOING.equals(status))
            return ContextCompat.getColor(ctx, R.color.accent_green);
        if (Shift.STATUS_COMPLETED.equals(status))
            return ContextCompat.getColor(ctx, R.color.divider);
        if (Shift.STATUS_CANCELLED.equals(status))
            return ContextCompat.getColor(ctx, R.color.error_red);
        return ContextCompat.getColor(ctx, R.color.primary_light);
    }

    @Override
    public int getItemCount() { return shiftList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvDay, tvTime, tvStatus, tvAttIcon;
        Button btnOpenAtt;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate     = itemView.findViewById(R.id.tv_date);
            tvDay      = itemView.findViewById(R.id.tv_day);
            tvTime     = itemView.findViewById(R.id.tv_time);
            tvStatus   = itemView.findViewById(R.id.tv_status);
            tvAttIcon  = itemView.findViewById(R.id.tv_att_icon);
            btnOpenAtt = itemView.findViewById(R.id.btn_open_attendance);
        }
    }
}
