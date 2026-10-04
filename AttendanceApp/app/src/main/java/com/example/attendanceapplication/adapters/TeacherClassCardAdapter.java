package com.example.attendanceapplication.adapters;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.utils.ClassCardUi;

import java.util.HashMap;
import java.util.List;
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
        holder.tvClassName.setText(classModel.getClassName());
        holder.tvClassId.setText(classModel.getClassId());
        ClassCardUi.bindAvatar(holder.tvAvatar, classModel.getClassName(), position);
        ClassCardUi.bindScheduleChips(holder.cgSchedule, classModel);
        holder.tvStudentCount.setText(classModel.getStudentCount() + " sinh viên");

        Shift shift = todayShiftMap.get(classModel.getClassId());
        if (shift == null || shift.getStatus() == null) {
            holder.tvStatus.setVisibility(View.GONE);
        } else {
            holder.tvStatus.setVisibility(View.VISIBLE);
            ClassCardUi.bindStatusPill(holder.tvStatus,
                    getStatusText(shift.getStatus()),
                    ClassCardUi.getStatusBackground(shift.getStatus()),
                    ClassCardUi.getStatusColor(shift.getStatus()));
        }

        holder.card.setOnClickListener(v -> listener.onClick(classModel));
        holder.ivMenu.setOnClickListener(v -> showMenu(holder, classModel));
    }

    @Override
    public int getItemCount() {
        return classList.size();
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
        ViewGroup cgSchedule;
        TextView tvAvatar, tvClassName, tvClassId, tvStudentCount, tvStatus;
        ImageView ivMenu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_class);
            tvAvatar = itemView.findViewById(R.id.tv_avatar);
            tvClassName = itemView.findViewById(R.id.tv_class_name);
            tvClassId = itemView.findViewById(R.id.tv_class_id);
            cgSchedule = itemView.findViewById(R.id.cg_schedule);
            tvStudentCount = itemView.findViewById(R.id.tv_student_count);
            tvStatus = itemView.findViewById(R.id.tv_status);
            ivMenu = itemView.findViewById(R.id.iv_menu);
        }
    }
}
