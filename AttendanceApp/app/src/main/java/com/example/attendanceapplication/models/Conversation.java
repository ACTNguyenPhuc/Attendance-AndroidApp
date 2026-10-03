package com.example.attendanceapplication.models;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Một cuộc trò chuyện đã lưu với trợ lý AI.
 *
 * Chỉ giữ phần tóm tắt để vẽ danh sách "Cuộc trò chuyện gần đây" — nội dung
 * tin nhắn nằm ở subcollection riêng, chỉ đọc khi người dùng thật sự mở ra.
 */
public class Conversation {

    private static final Locale VN = new Locale("vi", "VN");
    private static final SimpleDateFormat HHMM  = new SimpleDateFormat("HH:mm", VN);
    private static final SimpleDateFormat DDMM  = new SimpleDateFormat("dd/MM", VN);

    /** Id document Firestore. Không nằm trong dữ liệu ghi xuống. */
    private String id;
    /** Câu hỏi đầu tiên, đã cắt ngắn. */
    private String title;
    /** Câu trả lời gần nhất, đã cắt ngắn — dòng phụ trong danh sách. */
    private String preview;
    private long createdAt;
    private long updatedAt;
    private int messageCount;
    /**
     * Mạch hội thoại phía server. Có thể đã hết hạn (server dọn sau 30 phút);
     * khi đó server tự dựng lại context từ chính các tin nhắn đã lưu.
     */
    private String serverSessionId;
    /** Tool được gọi gần nhất — dùng để chọn biểu tượng cho dòng danh sách. */
    private String lastTool;

    public Conversation() { }

    /**
     * Nhãn thời gian bên phải mỗi dòng: hôm nay hiện giờ, hôm qua hiện chữ,
     * xa hơn hiện ngày tháng.
     */
    public String getTimeLabel() {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(updatedAt);
        Calendar now = Calendar.getInstance();

        if (sameDay(then, now)) return HHMM.format(new Date(updatedAt));

        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, now)) return "Hôm qua";

        return DDMM.format(new Date(updatedAt));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getTitle() { return title == null ? "" : title; }

    public void setTitle(String title) { this.title = title; }

    public String getPreview() { return preview == null ? "" : preview; }

    public void setPreview(String preview) { this.preview = preview; }

    public long getCreatedAt() { return createdAt; }

    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getMessageCount() { return messageCount; }

    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public String getServerSessionId() { return serverSessionId; }

    public void setServerSessionId(String serverSessionId) { this.serverSessionId = serverSessionId; }

    public String getLastTool() { return lastTool; }

    public void setLastTool(String lastTool) { this.lastTool = lastTool; }
}
