package com.example.attendanceapplication.models;

/**
 * Khung giờ học của MỘT ngày trong tuần. Mỗi lớp có thể có nhiều {@code DaySchedule},
 * mỗi thứ một giờ bắt đầu/kết thúc độc lập (vd T2 07:00-09:30, T4 13:00-15:00).
 *
 * <p>Là POJO Firestore: cần constructor rỗng + getter/setter cho {@code toObject()}.
 * Quy ước thứ theo VN: 2=T2 … 7=T7, 8=CN.
 */
public class DaySchedule {
    private int dayOfWeek;   // 2=Mon … 7=Sat, 8=Sun
    private String startAt;  // "07:00"
    private String endAt;    // "09:30"

    public DaySchedule() {}

    public DaySchedule(int dayOfWeek, String startAt, String endAt) {
        this.dayOfWeek = dayOfWeek;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }

    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }

    /** Nhãn hiển thị thứ: "CN" cho Chủ nhật, ngược lại "T2".."T7". */
    public String getDayLabel() {
        String[] days = {"", "CN", "T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        if (dayOfWeek >= 1 && dayOfWeek <= 8) return days[dayOfWeek];
        return "T" + dayOfWeek;
    }
}
