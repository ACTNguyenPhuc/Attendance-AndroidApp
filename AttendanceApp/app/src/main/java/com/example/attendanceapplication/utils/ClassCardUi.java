package com.example.attendanceapplication.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.DaySchedule;
import com.example.attendanceapplication.models.Shift;

import java.util.Locale;

/**
 * Các hàm dựng giao diện dùng chung cho thẻ lớp (trang chủ + "Lớp học của tôi") của giáo viên.
 */
public final class ClassCardUi {

    private static final int[][] AVATAR_COLORS = {
            {R.color.avatar_green_bg, R.color.avatar_green_fg},
            {R.color.avatar_yellow_bg, R.color.avatar_yellow_fg},
            {R.color.avatar_blue_bg, R.color.avatar_blue_fg}
    };

    private ClassCardUi() {}

    /** Ô chữ cái đầu tên lớp, màu nền nhạt xoay vòng xanh lá / vàng / xanh dương. */
    public static void bindAvatar(TextView tvAvatar, String className, int position) {
        Context ctx = tvAvatar.getContext();
        int[] pair = AVATAR_COLORS[position % AVATAR_COLORS.length];
        tvAvatar.setText(getInitial(className));
        tvAvatar.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, pair[0])));
        tvAvatar.setTextColor(ContextCompat.getColor(ctx, pair[1]));
    }

    /** Mỗi thứ trong lịch học là một chip "T2 07:00-09:25" (thứ in đậm). */
    public static void bindScheduleChips(ViewGroup group, ClassModel classModel) {
        group.removeAllViews();
        addScheduleChips(group, classModel, R.drawable.bg_chip_schedule,
                R.color.chip_schedule_day, R.color.chip_schedule_time);
        group.setVisibility(group.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** Thêm các chip lịch học (theo màu tuỳ chọn) vào cuối {@code group}. */
    public static void addScheduleChips(ViewGroup group, ClassModel classModel,
                                        int bgRes, int dayColorRes, int timeColorRes) {
        if (classModel.getDaySchedules() != null && !classModel.getDaySchedules().isEmpty()) {
            for (DaySchedule d : classModel.getDaySchedules()) {
                if (d == null) continue;
                group.addView(createChip(group.getContext(), d.getDayLabel(),
                        d.getStartAt() + "-" + d.getEndAt(), bgRes, dayColorRes, timeColorRes));
            }
        } else if (classModel.getSchedule() != null && !classModel.getSchedule().isEmpty()) {
            // Dữ liệu cũ: mọi thứ dùng chung một khung giờ.
            String time = classModel.getStartAt() != null && classModel.getEndAt() != null
                    ? classModel.getStartAt() + "-" + classModel.getEndAt() : "";
            for (Integer day : classModel.getSchedule()) {
                if (day == null) continue;
                group.addView(createChip(group.getContext(), day == 8 ? "CN" : "T" + day, time,
                        bgRes, dayColorRes, timeColorRes));
            }
        }
    }

    /** Pill trạng thái nền nhạt kèm chấm, vd "• Đang diễn ra". */
    public static void bindStatusPill(TextView tv, String text, int bgRes, int fgColorRes) {
        tv.setText("• " + text);
        tv.setBackgroundResource(bgRes);
        tv.setTextColor(ContextCompat.getColor(tv.getContext(), fgColorRes));
    }

    public static String getStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case Shift.STATUS_ONGOING: return "Đang diễn ra";
            case Shift.STATUS_UPCOMING: return "Sắp diễn ra";
            case Shift.STATUS_COMPLETED: return "Đã kết thúc";
            case Shift.STATUS_CANCELLED: return "Đã hủy";
            default: return status;
        }
    }

    public static int getStatusBackground(String status) {
        if (Shift.STATUS_ONGOING.equals(status)) return R.drawable.bg_pill_soft_green;
        if (Shift.STATUS_UPCOMING.equals(status)) return R.drawable.bg_pill_soft_orange;
        if (Shift.STATUS_CANCELLED.equals(status)) return R.drawable.bg_pill_soft_red;
        return R.drawable.bg_pill_soft_gray;
    }

    public static int getStatusColor(String status) {
        if (Shift.STATUS_ONGOING.equals(status)) return R.color.status_green_fg;
        if (Shift.STATUS_UPCOMING.equals(status)) return R.color.status_orange_fg;
        if (Shift.STATUS_CANCELLED.equals(status)) return R.color.status_red_fg;
        return R.color.status_gray_fg;
    }

    private static TextView createChip(Context ctx, String day, String time,
                                       int bgRes, int dayColorRes, int timeColorRes) {
        TextView chip = new TextView(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder(day);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, day.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ctx, dayColorRes)),
                0, day.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!time.isEmpty()) sb.append(" ").append(time);
        chip.setText(sb);
        chip.setTextColor(ContextCompat.getColor(ctx, timeColorRes));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        chip.setBackgroundResource(bgRes);
        int h = dp(ctx, 8), v = dp(ctx, 4);
        chip.setPadding(h, v, h, v);
        return chip;
    }

    private static String getInitial(String name) {
        if (name == null || name.trim().isEmpty()) return "G";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
