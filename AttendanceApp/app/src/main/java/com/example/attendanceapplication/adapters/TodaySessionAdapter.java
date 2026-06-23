package com.example.attendanceapplication.adapters;

import android.icu.util.Calendar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Shift;
import com.google.android.material.button.MaterialButton;

import java.sql.Time;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Card "Buổi học hôm nay" của sinh viên. Hiển thị nút "ĐIỂM DANH NGAY" chỉ khi
 * buổi đang mở điểm danh và sinh viên chưa điểm danh.
 */
public class TodaySessionAdapter extends RecyclerView.Adapter<TodaySessionAdapter.VH> {

    public interface OnCheckInListener {
        void onCheckIn(Shift shift);
    }

    private final List<Shift> shifts;
    private final Set<String> attendedShiftIds = new HashSet<>();
    private final OnCheckInListener listener;

    public TodaySessionAdapter(List<Shift> shifts, OnCheckInListener listener) {
        this.shifts = shifts;
        this.listener = listener;
    }

    public void setAttendedShiftIds(Set<String> ids) {
        attendedShiftIds.clear();
        if (ids != null) attendedShiftIds.addAll(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_today_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Shift s = shifts.get(position);

        h.tvClassName.setText(s.getClassName() != null ? s.getClassName() : s.getTitle());

        String time = (s.getStartAt() != null ? s.getStartAt() : "") + " - "
                + (s.getEndAt() != null ? s.getEndAt() : "");
        String room = s.getRoom() != null && !s.getRoom().isEmpty() ? "    📍 " + s.getRoom() : "";
        h.tvTimeRoom.setText("⏰ " + time + room);

        if (s.getTeacherName() != null && !s.getTeacherName().isEmpty()) {
            h.tvTeacher.setText("👤 " + s.getTeacherName());
            h.tvTeacher.setVisibility(View.VISIBLE);
        } else {
            h.tvTeacher.setVisibility(View.GONE);
        }

        boolean attended = attendedShiftIds.contains(s.getShiftId());
        boolean open = s.isAttendanceOpened() && Shift.STATUS_ONGOING.equals(s.getStatus());
        boolean notStarted = !isEndAtBeforeNow(s.getStartAt());

        // Luôn gán lại badge ở mọi lần bind để tránh giữ trạng thái cũ khi
        // ViewHolder bị tái sử dụng (vd: thấy "VẮNG" của card khác).
        h.tvBadge.setVisibility(View.VISIBLE);
        if (attended) {
            h.tvBadge.setText("ĐÃ ĐIỂM DANH");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
        } else if (open) {
            h.tvBadge.setText("ĐANG ĐIỂM DANH");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange);
        } else if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
            // Chỉ buổi đã kết thúc mà không có điểm danh mới tính là vắng.
            h.tvBadge.setText("VẮNG");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
        } else if (notStarted) {
            h.tvBadge.setText("SẮP DIỄN RA");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue);
        } else {
            h.tvBadge.setText("CHƯA MỞ");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_gray);
        }

        // Nút điểm danh ngay: chỉ khi đang mở điểm danh và chưa điểm danh.
        if (open && !attended) {
            h.btnCheckIn.setVisibility(View.VISIBLE);
            h.btnCheckIn.setOnClickListener(v -> {
                if (listener != null) listener.onCheckIn(s);
            });
        } else {
            h.btnCheckIn.setVisibility(View.GONE);
            h.btnCheckIn.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return shifts.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvClassName, tvBadge, tvTimeRoom, tvTeacher;
        MaterialButton btnCheckIn;

        VH(@NonNull View itemView) {
            super(itemView);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvBadge = itemView.findViewById(R.id.tv_badge);
            tvTimeRoom = itemView.findViewById(R.id.tv_time_room);
            tvTeacher = itemView.findViewById(R.id.tv_teacher);
            btnCheckIn = itemView.findViewById(R.id.btn_checkin);
        }
    }
    private boolean isEndAtBeforeNow(String endAt) {
        // endAt dạng "17:25"
        String[] parts = endAt.split(":");

        int endHour = Integer.parseInt(parts[0]);
        int endMinute = Integer.parseInt(parts[1]);

        Calendar now = Calendar.getInstance();

        int currentHour = now.get(Calendar.HOUR_OF_DAY); // 0 - 23
        int currentMinute = now.get(Calendar.MINUTE);
        int currentSecond = now.get(Calendar.SECOND);

        int endTotalSeconds = endHour * 3600 + endMinute * 60;
        int nowTotalSeconds = currentHour * 3600 + currentMinute * 60 + currentSecond;

        return endTotalSeconds < nowTotalSeconds;
    }
}
