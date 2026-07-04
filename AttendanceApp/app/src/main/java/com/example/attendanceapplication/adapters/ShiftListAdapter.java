package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShiftListAdapter extends RecyclerView.Adapter<ShiftListAdapter.ViewHolder> {

    public interface OnOpenAttendanceListener {
        void onOpen(Shift shift);
    }

    public interface OnShiftClickListener {
        void onClick(Shift shift);
    }

    public interface OnRescheduleListener {
        void onReschedule(Shift shift);
    }

    public interface OnDeleteListener {
        void onDelete(Shift shift);
    }

    private final List<Shift> shiftList;
    private final String classId;
    private final String className;
    private final OnOpenAttendanceListener listener;
    private final OnShiftClickListener shiftClickListener;
    private final OnRescheduleListener rescheduleListener;
    private final OnDeleteListener deleteListener;
    private String openedActionShiftId;

    public ShiftListAdapter(List<Shift> shiftList, String classId, String className,
                            OnOpenAttendanceListener listener,
                            OnShiftClickListener shiftClickListener,
                            OnRescheduleListener rescheduleListener,
                            OnDeleteListener deleteListener) {
        this.shiftList = shiftList;
        this.classId   = classId;
        this.className = className;
        this.listener  = listener;
        this.shiftClickListener = shiftClickListener;
        this.rescheduleListener = rescheduleListener;
        this.deleteListener = deleteListener;
    }

    /** Ca học chỉ được dời khi chưa mở điểm danh và chưa kết thúc/hủy. */
    public static boolean isReschedulable(Shift shift) {
        if (shift == null) return false;
        if (shift.isAttendanceOpened()) return false;
        String status = shift.getStatus();
        return !Shift.STATUS_COMPLETED.equals(status)
                && !Shift.STATUS_CANCELLED.equals(status);
    }

    /** Chỉ ca sắp diễn ra và chưa mở điểm danh mới được phép xóa. */
    public static boolean isDeletable(Shift shift) {
        return shift != null
                && Shift.STATUS_UPCOMING.equals(shift.getStatus())
                && !shift.isAttendanceOpened();
    }

    public static boolean hasSwipeActions(Shift shift) {
        return isReschedulable(shift) || isDeletable(shift);
    }

    /** Chỉ cho mở điểm danh đúng ngày và trong khung giờ [startAt, endAt) của ca. */
    public static boolean canOpenAttendanceAt(Shift shift, Date now) {
        return AttendanceUtils.canOpenAttendanceAt(shift, now);
    }

    public Shift getShiftAt(int position) {
        if (position < 0 || position >= shiftList.size()) return null;
        return shiftList.get(position);
    }

    public void openActions(int position) {
        Shift shift = getShiftAt(position);
        if (shift == null) return;

        String previousId = openedActionShiftId;
        openedActionShiftId = shift.getShiftId();
        if (previousId != null && !previousId.equals(openedActionShiftId)) {
            int previousPosition = findPositionById(previousId);
            if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition);
        }
        notifyItemChanged(position);
    }

    private void closeActions(Shift shift) {
        if (shift == null || shift.getShiftId() == null
                || !shift.getShiftId().equals(openedActionShiftId)) return;
        openedActionShiftId = null;
        int position = findPositionById(shift.getShiftId());
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position);
    }

    private boolean areActionsOpened(Shift shift) {
        return shift != null && shift.getShiftId() != null
                && shift.getShiftId().equals(openedActionShiftId);
    }

    private int findPositionById(String shiftId) {
        for (int i = 0; i < shiftList.size(); i++) {
            if (shiftId.equals(shiftList.get(i).getShiftId())) return i;
        }
        return RecyclerView.NO_POSITION;
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

        holder.tvDate.setText(formatDateVN(shift.getDate()));
        holder.tvTime.setText(shift.getStartAt() + " - " + shift.getEndAt());
        String room = shift.getRoom() == null ? "" : shift.getRoom().trim();
        holder.tvRoom.setText("Phòng: " + (room.isEmpty() ? "Chưa cập nhật" : room));
        holder.tvMakeupBadge.setVisibility(shift.isMakeup() ? View.VISIBLE : View.GONE);

        boolean attendanceInProgress = AttendanceUtils.isAttendanceInProgress(shift);
        int activeStrokeWidth = Math.round(2 * ctx.getResources().getDisplayMetrics().density);
        holder.foreground.setStrokeWidth(attendanceInProgress ? activeStrokeWidth : 0);
        holder.foreground.setStrokeColor(ContextCompat.getColor(ctx, R.color.accent_green));
        holder.foreground.setCardElevation(
                attendanceInProgress ? activeStrokeWidth * 4f : activeStrokeWidth);

        holder.foreground.animate().cancel();
        holder.foreground.setTranslationX(0f);
        boolean actionsOpened = areActionsOpened(shift);
        if (actionsOpened) {
            holder.actionPanel.post(() -> {
                int boundPosition = holder.getBindingAdapterPosition();
                Shift boundShift = getShiftAt(boundPosition);
                if (boundShift != null && openedActionShiftId != null
                        && openedActionShiftId.equals(boundShift.getShiftId())) {
                    holder.foreground.setTranslationX(-holder.actionPanel.getWidth());
                }
            });
        }

        boolean deletable = isDeletable(shift);
        holder.actionDelete.setEnabled(actionsOpened && deletable);
        holder.actionDelete.setClickable(actionsOpened && deletable);
        holder.actionDelete.setAlpha(deletable ? 1f : 0.45f);
        holder.actionDelete.setOnClickListener(v -> {
            if (!areActionsOpened(shift) || !isDeletable(shift) || deleteListener == null) return;
            closeActions(shift);
            deleteListener.onDelete(shift);
        });
        boolean reschedulable = isReschedulable(shift);
        holder.actionReschedule.setEnabled(actionsOpened && reschedulable);
        holder.actionReschedule.setClickable(actionsOpened && reschedulable);
        holder.actionReschedule.setAlpha(reschedulable ? 1f : 0.45f);
        holder.actionReschedule.setOnClickListener(v -> {
            if (!areActionsOpened(shift) || !isReschedulable(shift)
                    || rescheduleListener == null) return;
            closeActions(shift);
            rescheduleListener.onReschedule(shift);
        });

        // Status badge. "Sắp diễn ra" only shows when the shift is within 1 day.
        String status = shift.getStatus();
        boolean hideUpcoming = Shift.STATUS_UPCOMING.equals(status)
                && !com.example.attendanceapplication.utils.AttendanceUtils
                        .shouldShowUpcomingBadge(shift.getDate());
        if (hideUpcoming) {
            holder.tvStatus.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(getStatusText(status));
            holder.tvStatus.setBackgroundResource(getStatusBackground(status));
        }

        // Attendance info and action
        holder.btnOpenAtt.setOnClickListener(null);
        if (Shift.STATUS_COMPLETED.equals(status) || Shift.STATUS_CANCELLED.equals(status)) {
            holder.btnOpenAtt.setVisibility(View.GONE);
            holder.ivAttIcon.setVisibility(View.GONE);
            holder.tvAttInfo.setText("Đã kết thúc");
            holder.tvAttInfo.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        } else if (shift.isAttendanceOpened()) {
            holder.ivAttIcon.setVisibility(View.VISIBLE);
            holder.tvAttInfo.setText("Đã mở điểm danh");
            holder.tvAttInfo.setTextColor(ContextCompat.getColor(ctx, R.color.accent_green));
            holder.btnOpenAtt.setVisibility(View.GONE);
        } else {
            boolean canOpenAttendance = canOpenAttendanceAt(shift, new Date());
            holder.btnOpenAtt.setVisibility(canOpenAttendance ? View.VISIBLE : View.GONE);
            holder.ivAttIcon.setVisibility(View.GONE);
            holder.tvAttInfo.setText("Chưa mở điểm danh");
            holder.tvAttInfo.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            if (canOpenAttendance && listener != null) {
                holder.btnOpenAtt.setOnClickListener(v -> listener.onOpen(shift));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (shift.getShiftId() != null && shift.getShiftId().equals(openedActionShiftId)) {
                closeActions(shift);
                return;
            }
            if (shiftClickListener != null) shiftClickListener.onClick(shift);
        });
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

    private int getStatusBackground(String status) {
        if (Shift.STATUS_ONGOING.equals(status)) return R.drawable.bg_badge_green;
        if (Shift.STATUS_UPCOMING.equals(status)) return R.drawable.bg_badge_orange;
        if (Shift.STATUS_COMPLETED.equals(status)) return R.drawable.bg_badge_gray;
        if (Shift.STATUS_CANCELLED.equals(status)) return R.drawable.bg_badge_gray;
        return R.drawable.bg_badge_gray;
    }

    private String formatDateVN(String date) {
        if (date == null || date.isEmpty()) return "";
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date parsed = input.parse(date);
            if (parsed == null) return date;
            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);
            String[] days = {"Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
            String dayName = days[cal.get(Calendar.DAY_OF_WEEK) - 1];
            int dd = cal.get(Calendar.DAY_OF_MONTH);
            int mm = cal.get(Calendar.MONTH) + 1;
            int yy = cal.get(Calendar.YEAR);
            return String.format(Locale.getDefault(), "%s, %02d tháng %02d %d", dayName, dd, mm, yy);
        } catch (ParseException e) {
            return date;
        }
    }

    @Override
    public int getItemCount() { return shiftList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTime, tvRoom, tvStatus, tvAttInfo, tvMakeupBadge;
        ImageView ivAttIcon;
        MaterialButton btnOpenAtt;
        // Lớp foreground được dịch chuyển khi vuốt để lộ hai hành động phía sau.
        MaterialCardView foreground;
        View actionPanel, actionDelete, actionReschedule;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            foreground = itemView.findViewById(R.id.foreground);
            actionPanel = itemView.findViewById(R.id.swipe_action_panel);
            actionDelete = itemView.findViewById(R.id.action_delete);
            actionReschedule = itemView.findViewById(R.id.action_reschedule);
            tvDate     = itemView.findViewById(R.id.tv_date);
            tvTime     = itemView.findViewById(R.id.tv_time);
            tvRoom     = itemView.findViewById(R.id.tv_room);
            tvStatus   = itemView.findViewById(R.id.tv_status);
            tvMakeupBadge = itemView.findViewById(R.id.tv_makeup_badge);
            tvAttInfo  = itemView.findViewById(R.id.tv_att_info);
            ivAttIcon  = itemView.findViewById(R.id.iv_att_icon);
            btnOpenAtt = itemView.findViewById(R.id.btn_open_attendance);
        }
    }
}
