package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Shift;

import java.util.List;

public class ShiftHomeAdapter extends RecyclerView.Adapter<ShiftHomeAdapter.ViewHolder> {

    public interface OnShiftClickListener {
        void onClick(Shift shift);
    }

    private final List<Shift> shiftList;
    private final OnShiftClickListener listener;

    public ShiftHomeAdapter(List<Shift> shiftList, OnShiftClickListener listener) {
        this.shiftList = shiftList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shift_home_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shift shift = shiftList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvClassName.setText(shift.getClassName() != null ? shift.getClassName() : shift.getTitle());
        holder.tvTime.setText(shift.getStartAt() + " - " + shift.getEndAt());
        holder.tvRoom.setText(shift.getRoom() != null ? shift.getRoom() : "");

        String statusText = getStatusText(shift.getStatus());
        holder.tvStatus.setText(statusText);
        holder.tvStatus.setBackgroundColor(getStatusColor(ctx, shift.getStatus(), shift.isAttendanceOpened()));

        holder.card.setOnClickListener(v -> listener.onClick(shift));
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

    private int getStatusColor(Context ctx, String status, boolean attendanceOpened) {
        // "Chờ mở" in the stats means not opened; keep the badge green only when ongoing+opened.
        if (Shift.STATUS_ONGOING.equals(status) && attendanceOpened) {
            return ContextCompat.getColor(ctx, R.color.accent_green);
        }
        if (Shift.STATUS_ONGOING.equals(status) || Shift.STATUS_UPCOMING.equals(status)) {
            return ContextCompat.getColor(ctx, R.color.primary_light);
        }
        if (Shift.STATUS_COMPLETED.equals(status)) {
            return ContextCompat.getColor(ctx, R.color.divider);
        }
        if (Shift.STATUS_CANCELLED.equals(status)) {
            return ContextCompat.getColor(ctx, R.color.error_red);
        }
        return ContextCompat.getColor(ctx, R.color.primary_light);
    }

    @Override
    public int getItemCount() {
        return shiftList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvClassName, tvTime, tvRoom, tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_shift_home);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvRoom = itemView.findViewById(R.id.tv_room);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}

