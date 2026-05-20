package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.utils.AttendanceUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AttendanceHistoryAdapter extends RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder> {

    private final List<Attendance> list;
    private final SimpleDateFormat dtFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AttendanceHistoryAdapter(List<Attendance> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendance att = list.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvClassId.setText(att.getClassId());
        holder.tvShiftId.setText(att.getShiftId());

        if (att.getCheckinTime() != null) {
            holder.tvDateTime.setText(dtFormat.format(att.getCheckinTime().toDate()));
        } else {
            holder.tvDateTime.setText("—");
        }

        holder.tvDistance.setText("Khoảng cách: " + AttendanceUtils.formatDistance(att.getDistance()));

        String status = att.getStatus();
        if (Attendance.STATUS_PRESENT.equals(status)) {
            holder.tvStatusBadge.setText("ĐÃ ĐIỂM DANH");
            holder.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.accent_green));
            holder.tvStatusIcon.setText("✅");
        } else if (Attendance.STATUS_LATE.equals(status)) {
            holder.tvStatusBadge.setText("MUỘN");
            holder.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.warning_yellow));
            holder.tvStatusIcon.setText("⏰");
        } else {
            holder.tvStatusBadge.setText("VẮNG");
            holder.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(ctx, R.color.error_red));
            holder.tvStatusIcon.setText("❌");
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvClassId, tvShiftId, tvDateTime, tvDistance, tvStatusBadge, tvStatusIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvClassId     = itemView.findViewById(R.id.tv_class_id);
            tvShiftId     = itemView.findViewById(R.id.tv_shift_id);
            tvDateTime    = itemView.findViewById(R.id.tv_datetime);
            tvDistance    = itemView.findViewById(R.id.tv_distance);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvStatusIcon  = itemView.findViewById(R.id.tv_status_icon);
        }
    }
}
