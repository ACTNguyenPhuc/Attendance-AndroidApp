package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;

public class Shift {
    public static final String STATUS_UPCOMING = "upcoming";
    public static final String STATUS_ONGOING = "ongoing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    private String shiftId;
    private String classId;
    private String className;
    private String teacherName;
    private String title;
    private String date;        // YYYY-MM-DD
    private int dayOfWeek;      // 2=Mon ... 7=Sat
    private String startAt;
    private String endAt;
    private String room;
    private String status;
    private boolean attendanceOpened;
    // true nếu là ca học bù do giáo viên thêm ngoài lịch học gốc của lớp.
    private boolean makeup;
    private String attendanceSessionId;
    // Nội dung buổi học do giáo viên nhập khi đóng phiên (vd "Buổi 8 - Giao thức TCP/IP").
    private String content;
    private Timestamp createdAt;

    // Local state (not stored in Firestore)
    private String attendanceStatus; // present / absent / not_checked

    public Shift() {}

    // Getters & Setters
    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }

    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isAttendanceOpened() { return attendanceOpened; }
    public void setAttendanceOpened(boolean attendanceOpened) { this.attendanceOpened = attendanceOpened; }

    public boolean isMakeup() { return makeup; }
    public void setMakeup(boolean makeup) { this.makeup = makeup; }

    public String getAttendanceSessionId() { return attendanceSessionId; }
    public void setAttendanceSessionId(String attendanceSessionId) { this.attendanceSessionId = attendanceSessionId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(String attendanceStatus) { this.attendanceStatus = attendanceStatus; }

    public String getDayOfWeekDisplay() {
        // The app stores Monday–Sunday as 2–8; Sunday must display as "CN", not "T8".
        String[] days = {"", "CN", "T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        if (dayOfWeek >= 1 && dayOfWeek <= 8) return days[dayOfWeek];
        return "T" + dayOfWeek;
    }
}
