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
import com.example.attendanceapplication.utils.ClassCardUi;
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
        holder.tvStartTime.setText(shift.getStartAt() != null ? shift.getStartAt() : "");
        holder.tvEndTime.setText(shift.getEndAt() != null ? shift.getEndAt() : "");
        String room = shift.getRoom() == null ? "" : shift.getRoom().trim();
        holder.tvRoom.setText(room.isEmpty() ? "Chưa cập nhật phòng" : "Phòng " + room);
        holder.tvMakeupBadge.setVisibility(shift.isMakeup() ? View.VISIBLE : View.GONE);

        // Tiêu đề nhóm chỉ hiện ở thẻ đầu tiên của mỗi nhóm.
        String section = getSection(shift);
        Shift previous = position > 0 ? shiftList.get(position - 1) : null;
        boolean showHeader = previous == null || !section.equals(getSection(previous));
        holder.tvSectionHeader.setVisibility(showHeader ? View.VISIBLE : View.GONE);
        holder.tvSectionHeader.setText(section);

        boolean attendanceInProgress = AttendanceUtils.isAttendanceInProgress(shift);
        float density = ctx.getResources().getDisplayMetrics().density;
        holder.foreground.setStrokeWidth(Math.round((attendanceInProgress ? 1.5f : 1f) * density));
        holder.foreground.setStrokeColor(ContextCompat.getColor(ctx,
                attendanceInProgress ? R.color.status_green_fg : R.color.card_stroke));
        holder.foreground.setCardElevation(attendanceInProgress ? 3 * density : density);

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
                && !AttendanceUtils.shouldShowUpcomingBadge(shift.getDate());
        if (hideUpcoming) {
            holder.tvStatus.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setVisibility(View.VISIBLE);
            ClassCardUi.bindStatusPill(holder.tvStatus, ClassCardUi.getStatusText(status),
                    ClassCardUi.getStatusBackground(status), ClassCardUi.getStatusColor(status));
        }
        int barColor = attendanceInProgress || Shift.STATUS_ONGOING.equals(status)
                ? R.color.status_green_fg
                : Shift.STATUS_UPCOMING.equals(status) ? R.color.status_orange_fg
                : R.color.shift_bar_gray;
        holder.viewStatusBar.setBackgroundColor(ContextCompat.getColor(ctx, barColor));

        // Dải trạng thái điểm danh: ẩn với ca đã kết thúc/hủy và ca chưa tới ngày.
        holder.btnOpenAtt.setOnClickListener(null);
        boolean finished = Shift.STATUS_COMPLETED.equals(status) || Shift.STATUS_CANCELLED.equals(status);
        if (finished) {
            holder.attFooter.setVisibility(View.GONE);
        } else if (shift.isAttendanceOpened()) {
            holder.attFooter.setVisibility(View.VISIBLE);
            holder.attFooter.setBackgroundColor(ContextCompat.getColor(ctx, R.color.shift_footer_green));
            holder.ivAttIcon.setVisibility(View.VISIBLE);
            holder.tvAttInfo.setText("Đã mở điểm danh");
            holder.tvAttInfo.setTextColor(ContextCompat.getColor(ctx, R.color.status_green_fg));
            holder.btnOpenAtt.setVisibility(View.GONE);
        } else if (todayString().equals(shift.getDate())) {
            boolean canOpenAttendance = canOpenAttendanceAt(shift, new Date());
            holder.attFooter.setVisibility(View.VISIBLE);
            holder.attFooter.setBackgroundColor(ContextCompat.getColor(ctx, R.color.shift_footer_gray));
            holder.ivAttIcon.setVisibility(View.GONE);
            holder.tvAttInfo.setText("Chưa mở điểm danh");
            holder.tvAttInfo.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            holder.btnOpenAtt.setVisibility(canOpenAttendance ? View.VISIBLE : View.GONE);
            if (canOpenAttendance && listener != null) {
                holder.btnOpenAtt.setOnClickListener(v -> listener.onOpen(shift));
            }
        } else {
            holder.attFooter.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (shift.getShiftId() != null && shift.getShiftId().equals(openedActionShiftId)) {
                closeActions(shift);
                return;
            }
            if (shiftClickListener != null) shiftClickListener.onClick(shift);
        });
    }



    public static final String SECTION_TODAY = "HÔM NAY";
    public static final String SECTION_UPCOMING = "SẮP DIỄN RA";
    public static final String SECTION_FINISHED = "ĐÃ KẾT THÚC";

    /** Nhóm hiển thị của ca: đang điểm danh / hôm nay → sắp diễn ra → đã kết thúc. */
    public static String getSection(Shift shift) {
        if (AttendanceUtils.isAttendanceInProgress(shift)) return SECTION_TODAY;
        String status = shift.getStatus();
        if (Shift.STATUS_COMPLETED.equals(status) || Shift.STATUS_CANCELLED.equals(status)) {
            return SECTION_FINISHED;
        }
        if (todayString().equals(shift.getDate())) return SECTION_TODAY;
        return SECTION_UPCOMING;
    }

    /** Thứ tự nhóm để sắp xếp danh sách (0 = hôm nay, 1 = sắp diễn ra, 2 = đã kết thúc). */
    public static int getSectionOrder(Shift shift) {
        String section = getSection(shift);
        if (SECTION_TODAY.equals(section)) return 0;
        if (SECTION_UPCOMING.equals(section)) return 1;
        return 2;
    }

    private static String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
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
        TextView tvSectionHeader, tvDate, tvStartTime, tvEndTime, tvRoom, tvStatus, tvAttInfo, tvMakeupBadge;
        View viewStatusBar, attFooter;
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
            tvSectionHeader = itemView.findViewById(R.id.tv_section_header);
            tvStartTime = itemView.findViewById(R.id.tv_start_time);
            tvEndTime  = itemView.findViewById(R.id.tv_end_time);
            viewStatusBar = itemView.findViewById(R.id.view_status_bar);
            attFooter  = itemView.findViewById(R.id.layout_att_footer);
            tvRoom     = itemView.findViewById(R.id.tv_room);
            tvStatus   = itemView.findViewById(R.id.tv_status);
            tvMakeupBadge = itemView.findViewById(R.id.tv_makeup_badge);
            tvAttInfo  = itemView.findViewById(R.id.tv_att_info);
            ivAttIcon  = itemView.findViewById(R.id.iv_att_icon);
            btnOpenAtt = itemView.findViewById(R.id.btn_open_attendance);
        }
    }
}
