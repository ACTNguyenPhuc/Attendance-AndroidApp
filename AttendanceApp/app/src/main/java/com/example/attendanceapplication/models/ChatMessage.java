package com.example.attendanceapplication.models;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Một tin nhắn trong khung chat với trợ lý AI. */
public class ChatMessage {

    public static final int ROLE_USER = 0;
    public static final int ROLE_AI   = 1;
    /** Dòng phân cách ngày, ví dụ "Hôm nay, 16:33". Không phải tin nhắn thật. */
    public static final int ROLE_DATE = 2;

    private static final SimpleDateFormat HHMM =
            new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));
    private static final SimpleDateFormat DDMM =
            new SimpleDateFormat("dd/MM", new Locale("vi", "VN"));

    private int role;
    private String text;
    /** Ghi chú "đang tra cứu ..." hiện phía trên câu trả lời. Có thể null. */
    private String toolNote;
    /** true khi AI còn đang gõ dở, dùng để hiện dấu ba chấm. */
    private boolean streaming;
    private long createdAt = System.currentTimeMillis();

    public ChatMessage() { }

    public ChatMessage(int role, String text) {
        this.role = role;
        this.text = text;
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(ROLE_USER, text);
    }

    public static ChatMessage aiPending() {
        ChatMessage m = new ChatMessage(ROLE_AI, "");
        m.streaming = true;
        return m;
    }

    public static ChatMessage dateSeparator() {
        return dateSeparator(System.currentTimeMillis());
    }

    /**
     * Dòng ngày cho một mốc thời gian bất kỳ.
     *
     * Hội thoại mở lại từ lịch sử có thể của hôm qua hay tuần trước, nên không
     * thể mặc định là "Hôm nay" như lúc đang chat.
     */
    public static ChatMessage dateSeparator(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar ref = Calendar.getInstance();

        String ngay;
        if (sameDay(then, ref)) {
            ngay = "Hôm nay";
        } else {
            ref.add(Calendar.DAY_OF_YEAR, -1);
            ngay = sameDay(then, ref) ? "Hôm qua" : DDMM.format(new Date(millis));
        }
        return new ChatMessage(ROLE_DATE, ngay + ", " + HHMM.format(new Date(millis)));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Giờ hiển thị dưới bong bóng, dạng HH:mm. */
    public String getTimeLabel() {
        return HHMM.format(new Date(createdAt));
    }

    public int getRole() { return role; }

    public void setRole(int role) { this.role = role; }

    public String getText() { return text == null ? "" : text; }

    public void setText(String text) { this.text = text; }

    public void append(String delta) {
        this.text = (this.text == null ? "" : this.text) + delta;
    }

    public String getToolNote() { return toolNote; }

    public void setToolNote(String toolNote) { this.toolNote = toolNote; }

    public boolean isStreaming() { return streaming; }

    public void setStreaming(boolean streaming) { this.streaming = streaming; }

    public long getCreatedAt() { return createdAt; }

    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
