package com.example.attendanceapplication.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Conversation;
import com.example.attendanceapplication.utils.MarkdownRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh sách "Cuộc trò chuyện gần đây" trong màn hình chat trống.
 *
 * Mỗi dòng có một ô biểu tượng màu suy ra từ tool mà hội thoại đó dùng gần nhất,
 * để nhìn lướt là đoán được hội thoại nói về lịch học hay về chuyên cần.
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {

    /** Tên tool bên server -> biểu tượng và cặp màu (nền nhạt, chữ đậm). */
    private static final Map<String, int[]> TOOL_STYLE = new HashMap<>();

    static {
        // {icon, màu nền ô, màu biểu tượng}
        TOOL_STYLE.put("get_my_classes",
                new int[]{R.drawable.ic_school,      0xFFE6F4EA, 0xFF1E8E3E});
        TOOL_STYLE.put("get_my_schedule",
                new int[]{R.drawable.ic_event,       0xFFE8F0FE, 0xFF1A73E8});
        TOOL_STYLE.put("get_my_attendance_summary",
                new int[]{R.drawable.ic_trending_up, 0xFFFCE8E6, 0xFFD93025});
        TOOL_STYLE.put("get_attendance_history",
                new int[]{R.drawable.ic_check_circle, 0xFFF3E8FD, 0xFF8430CE});
        TOOL_STYLE.put("get_students_at_risk",
                new int[]{R.drawable.ic_group,       0xFFFEF7E0, 0xFFF29900});
        TOOL_STYLE.put("get_shift_attendance",
                new int[]{R.drawable.ic_clock,       0xFFE0F7FA, 0xFF00838F});
    }

    /** Hội thoại chưa gọi tool nào — dùng biểu tượng chat mặc định. */
    private static final int[] DEFAULT_STYLE =
            {R.drawable.ic_chat_blue, 0xFFE3F0FC, 0xFF0F50AA};

    public interface Listener {
        void onOpen(Conversation c);

        void onDelete(Conversation c);
    }

    private final List<Conversation> items;
    private final Listener listener;

    public ConversationAdapter(List<Conversation> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Conversation c = items.get(position);

        h.tvTitle.setText(c.getTitle());
        // Bản ghi lưu trước khi có MarkdownRenderer.plain() vẫn còn dấu sao,
        // nên gỡ thêm một lần nữa lúc hiển thị.
        h.tvPreview.setText(MarkdownRenderer.plain(c.getPreview()));
        h.tvTime.setText(c.getTimeLabel());

        int[] style = TOOL_STYLE.get(c.getLastTool());
        if (style == null) style = DEFAULT_STYLE;
        h.ivIcon.setImageResource(style[0]);
        h.ivIcon.setBackgroundTintList(ColorStateList.valueOf(style[1]));
        // Các vector icon đã gắn sẵn android:tint, phải ghi đè bằng bộ lọc màu
        h.ivIcon.setColorFilter(style[2]);

        h.itemView.setOnClickListener(v -> listener.onOpen(c));
        h.itemView.setOnLongClickListener(v -> {
            listener.onDelete(c);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvPreview;
        final TextView tvTime;

        Holder(@NonNull View v) {
            super(v);
            ivIcon    = v.findViewById(R.id.iv_conv_icon);
            tvTitle   = v.findViewById(R.id.tv_conv_title);
            tvPreview = v.findViewById(R.id.tv_conv_preview);
            tvTime    = v.findViewById(R.id.tv_conv_time);
        }
    }
}
