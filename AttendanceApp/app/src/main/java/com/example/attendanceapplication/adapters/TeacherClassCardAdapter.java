package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeacherClassCardAdapter extends RecyclerView.Adapter<TeacherClassCardAdapter.ViewHolder> {

    public interface OnClassClickListener {
        void onClick(ClassModel classModel);
    }

    public interface OnClassMenuListener {
        void onViewDetail(ClassModel classModel);
        void onShowQr(ClassModel classModel);
        void onDelete(ClassModel classModel);
    }

    private final List<ClassModel> classList;
    private final OnClassClickListener listener;
    private final OnClassMenuListener menuListener;
    private Map<String, Shift> todayShiftMap = new HashMap<>();

    public TeacherClassCardAdapter(List<ClassModel> classList,
                                   OnClassClickListener listener,
                                   OnClassMenuListener menuListener) {
        this.classList = classList;
        this.listener = listener;
        this.menuListener = menuListener;
    }

    public void setTodayShiftMap(Map<String, Shift> todayShiftMap) {
        this.todayShiftMap = todayShiftMap != null ? todayShiftMap : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_card_teacher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassModel classModel = classList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvClassName.setText(classModel.getClassName());
        holder.tvClassId.setText(classModel.getClassId());
        holder.tvSchedule.setText(classModel.getScheduleDisplay());
        holder.tvTime.setText(classModel.getStartAt() + "-" + classModel.getEndAt());
        holder.tvStudentCount.setText(classModel.getStudentCount() + " sinh viên");

        holder.tvAvatar.setText(getInitials(classModel.getClassName()));
        holder.header.setBackgroundColor(getHeaderColor(ctx, position));

        Shift shift = todayShiftMap.get(classModel.getClassId());
        if (shift == null || shift.getStatus() == null) {
            holder.tvStatus.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(getStatusText(shift.getStatus()));
            holder.tvStatus.setBackgroundResource(getStatusBackground(shift.getStatus()));
        }

        holder.card.setOnClickListener(v -> listener.onClick(classModel));
        holder.ivMenu.setOnClickListener(v -> showMenu(holder, classModel));
    }

    @Override
    public int getItemCount() {
        return classList.size();
    }

    private int getHeaderColor(Context ctx, int position) {
        int[] colors = new int[] {
                ContextCompat.getColor(ctx, R.color.accent_green),
                ContextCompat.getColor(ctx, R.color.accent_yellow),
                ContextCompat.getColor(ctx, R.color.primary_light)
        };
        int index = position % colors.length;
        return colors[index];
    }

    private String getInitials(String name) {
        if (name == null) return "G";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "G";
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private String getStatusText(String status) {
        switch (status) {
            case Shift.STATUS_ONGOING:
                return "Đang diễn ra";
            case Shift.STATUS_UPCOMING:
                return "Sắp diễn ra";
            case Shift.STATUS_COMPLETED:
                return "Đã hoàn thành";
            case Shift.STATUS_CANCELLED:
                return "Đã hủy";
            default:
                return status;
        }
    }

    private int getStatusBackground(String status) {
        switch (status) {
            case Shift.STATUS_ONGOING:
                return R.drawable.bg_badge_green;
            case Shift.STATUS_UPCOMING:
                return R.drawable.bg_badge_orange;
            case Shift.STATUS_COMPLETED:
                return R.drawable.bg_badge_gray;
            case Shift.STATUS_CANCELLED:
                return R.drawable.bg_badge_gray;
            default:
                return R.drawable.bg_badge_gray;
        }
    }

    private void showMenu(ViewHolder holder, ClassModel classModel) {
        PopupMenu popupMenu = new PopupMenu(holder.itemView.getContext(), holder.ivMenu);
        popupMenu.getMenu().add(0, 1, 0, "Xem chi tiết");
        popupMenu.getMenu().add(0, 2, 1, "Tạo QR tham gia");

        SpannableString deleteTitle = new SpannableString("Xóa lớp");
        int red = ContextCompat.getColor(holder.itemView.getContext(), R.color.error_red);
        deleteTitle.setSpan(new ForegroundColorSpan(red), 0, deleteTitle.length(), 0);
        popupMenu.getMenu().add(0, 3, 2, deleteTitle);

        popupMenu.setOnMenuItemClickListener(item -> handleMenuItem(item, classModel));
        popupMenu.show();
    }

    private boolean handleMenuItem(MenuItem item, ClassModel classModel) {
        if (menuListener == null) return false;
        int id = item.getItemId();
        if (id == 1) {
            menuListener.onViewDetail(classModel);
            return true;
        }
        if (id == 2) {
            menuListener.onShowQr(classModel);
            return true;
        }
        if (id == 3) {
            menuListener.onDelete(classModel);
            return true;
        }
        return false;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        LinearLayout header;
        TextView tvAvatar, tvClassName, tvClassId, tvSchedule, tvTime, tvStudentCount, tvStatus;
        ImageView ivMenu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_class);
            header = itemView.findViewById(R.id.layout_header);
            tvAvatar = itemView.findViewById(R.id.tv_avatar);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvClassId = itemView.findViewById(R.id.tv_class_id);
            tvSchedule = itemView.findViewById(R.id.tv_schedule_chip);
            tvTime = itemView.findViewById(R.id.tv_time_chip);
            tvStudentCount = itemView.findViewById(R.id.tv_student_count);
            tvStatus = itemView.findViewById(R.id.tv_status);
            ivMenu = itemView.findViewById(R.id.iv_menu);
        }
    }
}
