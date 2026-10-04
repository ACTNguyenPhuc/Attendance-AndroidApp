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
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.example.attendanceapplication.utils.ClassCardUi;

import java.util.Date;
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
        holder.tvStartTime.setText(shift.getStartAt() != null ? shift.getStartAt() : "");
        holder.tvEndTime.setText(shift.getEndAt() != null ? shift.getEndAt() : "");
        String room = shift.getRoom();
        holder.tvRoom.setText(room != null && !room.trim().isEmpty() ? room : "Chưa có phòng");

        Date now = new Date();
        boolean attendanceNow = AttendanceUtils.canOpenAttendanceAt(shift, now);
        String status = shift.getStatus();
        int colorRes;
        if (attendanceNow) {
            colorRes = R.color.status_green_fg;
            ClassCardUi.bindStatusPill(holder.tvStatus, "Điểm danh ngay",
                    R.drawable.bg_pill_soft_green, colorRes);
        } else {
            colorRes = ClassCardUi.getStatusColor(status);
            ClassCardUi.bindStatusPill(holder.tvStatus, ClassCardUi.getStatusText(status),
                    ClassCardUi.getStatusBackground(status), colorRes);
        }
        holder.viewStatusBar.setBackgroundColor(ContextCompat.getColor(ctx, colorRes));
        holder.viewDivider.setVisibility(position == shiftList.size() - 1 ? View.GONE : View.VISIBLE);

        boolean canOpenCard = Shift.STATUS_COMPLETED.equals(status)
                || shift.isAttendanceOpened()
                || attendanceNow;
        holder.card.setOnClickListener(null);
        holder.card.setClickable(canOpenCard);
        holder.card.setFocusable(canOpenCard);
        if (canOpenCard && listener != null) {
            holder.card.setOnClickListener(v -> listener.onClick(shift));
        }
    }

    @Override
    public int getItemCount() {
        return shiftList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View card, viewStatusBar, viewDivider;
        TextView tvClassName, tvStartTime, tvEndTime, tvRoom, tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_shift_home);
            viewStatusBar = itemView.findViewById(R.id.view_status_bar);
            viewDivider = itemView.findViewById(R.id.view_divider);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvStartTime = itemView.findViewById(R.id.tv_start_time);
            tvEndTime = itemView.findViewById(R.id.tv_end_time);
            tvRoom = itemView.findViewById(R.id.tv_room);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
