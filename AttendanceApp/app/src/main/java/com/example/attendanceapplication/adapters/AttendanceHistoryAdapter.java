package com.example.attendanceapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.utils.AttendanceUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lịch sử điểm danh của sinh viên: gồm header theo tháng và các card buổi học
 * có thể bấm để mở rộng xem chi tiết (khoảng cách, thời gian, tọa độ).
 * Mỗi phần tử trong {@code rows} là một {@link String} (header tháng) hoặc
 * một {@link HistoryItem}.
 */
public class AttendanceHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    /** Một dòng buổi học trong lịch sử (dữ liệu đã dựng sẵn từ shift + attendance). */
    public static class HistoryItem {
        public String className;
        public String label;        // vd "Buổi 8 - Giao thức TCP/IP"
        public String dateText;     // vd "8/5/2026"
        public String status;       // present / late / absent
        public boolean hasAttendance;
        public double distance;
        public double latitude;
        public double longitude;
        public Date checkinTime;    // null nếu vắng
        public boolean expanded;
    }

    private final List<Object> rows;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public AttendanceHistoryAdapter(List<Object> rows) {
        this.rows = rows;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof HistoryItem ? TYPE_ITEM : TYPE_HEADER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inflater.inflate(R.layout.item_history_month_header, parent, false));
        }
        return new ItemVH(inflater.inflate(R.layout.item_attendance_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).tvMonth.setText((String) rows.get(position));
            return;
        }

        ItemVH h = (ItemVH) holder;
        HistoryItem it = (HistoryItem) rows.get(position);
        Context ctx = h.itemView.getContext();

        h.tvClassName.setText(it.className);
        h.tvLabel.setText(it.label);

        if (it.hasAttendance && it.checkinTime != null) {
            h.tvDateTime.setText(it.dateText + "  •  " + timeFormat.format(it.checkinTime));
        } else {
            h.tvDateTime.setText(it.dateText);
        }

        // Badge + icon theo trạng thái.
        if (Attendance.STATUS_PRESENT.equals(it.status)) {
            h.tvBadge.setText("ĐÃ ĐIỂM DANH");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
            h.tvIcon.setText("✅");
        } else if (Attendance.STATUS_LATE.equals(it.status)) {
            h.tvBadge.setText("MUỘN");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_orange);
            h.tvIcon.setText("⏰");
        } else {
            h.tvBadge.setText("VẮNG");
            h.tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
            h.tvIcon.setText("❌");
        }

        // Chi tiết mở rộng.
        if (it.hasAttendance) {
            h.tvDistance.setText("📍 Khoảng cách: " + AttendanceUtils.formatDistance(it.distance)
                    + " từ lớp học");
            h.tvExactTime.setText("🕐 Thời gian chính xác: "
                    + (it.checkinTime != null ? timeFormat.format(it.checkinTime) : "--"));
            h.tvCoords.setText("🌐 Tọa độ: " + formatCoords(it.latitude, it.longitude));
            h.tvExactTime.setVisibility(View.VISIBLE);
            h.tvCoords.setVisibility(View.VISIBLE);
        } else {
            h.tvDistance.setText("Bạn không điểm danh buổi học này.");
            h.tvExactTime.setVisibility(View.GONE);
            h.tvCoords.setVisibility(View.GONE);
        }

        h.layoutDetail.setVisibility(it.expanded ? View.VISIBLE : View.GONE);
        h.ivExpand.setRotation(it.expanded ? 270 : 90);

        h.layoutHeader.setOnClickListener(v -> {
            it.expanded = !it.expanded;
            notifyItemChanged(h.getAdapterPosition());
        });
    }

    private String formatCoords(double lat, double lng) {
        String latStr = String.format(Locale.US, "%.4f°%s", Math.abs(lat), lat >= 0 ? "N" : "S");
        String lngStr = String.format(Locale.US, "%.4f°%s", Math.abs(lng), lng >= 0 ? "E" : "W");
        return latStr + ", " + lngStr;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvMonth;
        HeaderVH(@NonNull View v) {
            super(v);
            tvMonth = v.findViewById(R.id.tv_month);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        LinearLayout layoutHeader, layoutDetail;
        TextView tvClassName, tvLabel, tvDateTime, tvBadge, tvIcon, tvDistance, tvExactTime, tvCoords;
        ImageView ivExpand;

        ItemVH(@NonNull View v) {
            super(v);
            layoutHeader = v.findViewById(R.id.layout_header);
            layoutDetail = v.findViewById(R.id.layout_detail);
            tvClassName = v.findViewById(R.id.tv_class_name);
            tvLabel = v.findViewById(R.id.tv_label);
            tvDateTime = v.findViewById(R.id.tv_datetime);
            tvBadge = v.findViewById(R.id.tv_status_badge);
            tvIcon = v.findViewById(R.id.tv_status_icon);
            tvDistance = v.findViewById(R.id.tv_distance);
            tvExactTime = v.findViewById(R.id.tv_exact_time);
            tvCoords = v.findViewById(R.id.tv_coords);
            ivExpand = v.findViewById(R.id.iv_expand);
        }
    }
}
