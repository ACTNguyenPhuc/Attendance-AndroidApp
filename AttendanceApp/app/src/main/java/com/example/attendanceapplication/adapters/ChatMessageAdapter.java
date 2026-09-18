package com.example.attendanceapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.ChatMessage;
import com.example.attendanceapplication.utils.MarkdownRenderer;

import java.util.List;

/**
 * Hiển thị danh sách tin nhắn chat. Ba kiểu dòng: người dùng, trợ lý, và dòng ngày.
 *
 * Bong bóng của trợ lý đi kèm ảnh đại diện, một dòng ghi chú nhỏ lúc đang tra cứu
 * ("Đang tra lịch học…") và giờ gửi phía dưới.
 *
 * Văn bản của trợ lý đi qua {@link MarkdownRenderer} — nếu không, người dùng sẽ
 * nhìn thấy nguyên các ký tự sao của Markdown.
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_AI   = 1;
    private static final int TYPE_DATE = 2;

    private final List<ChatMessage> items;

    public ChatMessageAdapter(List<ChatMessage> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        int role = items.get(position).getRole();
        if (role == ChatMessage.ROLE_USER) return TYPE_USER;
        if (role == ChatMessage.ROLE_DATE) return TYPE_DATE;
        return TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        }
        if (viewType == TYPE_DATE) {
            return new DateHolder(inflater.inflate(R.layout.item_chat_date, parent, false));
        }
        return new AiHolder(inflater.inflate(R.layout.item_chat_ai, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = items.get(position);

        if (holder instanceof DateHolder) {
            ((DateHolder) holder).tvDate.setText(m.getText());
            return;
        }

        if (holder instanceof UserHolder) {
            UserHolder u = (UserHolder) holder;
            u.tvText.setText(m.getText());
            u.tvTime.setText(m.getTimeLabel());
            return;
        }

        AiHolder h = (AiHolder) holder;

        String note = m.getToolNote();
        if (note == null || note.isEmpty()) {
            h.tvTool.setVisibility(View.GONE);
        } else {
            h.tvTool.setVisibility(View.VISIBLE);
            h.tvTool.setText(note);
        }

        String text = m.getText();
        if (text.isEmpty() && m.isStreaming()) {
            // Chưa có chữ nào mà vẫn đang chờ -> hiện dấu hiệu đang gõ
            h.tvText.setText("• • •");
        } else {
            h.tvText.setText(MarkdownRenderer.render(text));
        }

        // Còn đang gõ dở thì chưa hiện giờ, tránh nhảy layout liên tục
        if (m.isStreaming()) {
            h.tvTime.setVisibility(View.GONE);
        } else {
            h.tvTime.setVisibility(View.VISIBLE);
            h.tvTime.setText(m.getTimeLabel());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserHolder extends RecyclerView.ViewHolder {
        final TextView tvText;
        final TextView tvTime;

        UserHolder(@NonNull View v) {
            super(v);
            tvText = v.findViewById(R.id.tv_chat_text);
            tvTime = v.findViewById(R.id.tv_chat_time);
        }
    }

    static class AiHolder extends RecyclerView.ViewHolder {
        final TextView tvText;
        final TextView tvTool;
        final TextView tvTime;

        AiHolder(@NonNull View v) {
            super(v);
            tvText = v.findViewById(R.id.tv_chat_text);
            tvTool = v.findViewById(R.id.tv_chat_tool);
            tvTime = v.findViewById(R.id.tv_chat_time);
        }
    }

    static class DateHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;

        DateHolder(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_chat_date);
        }
    }
}
