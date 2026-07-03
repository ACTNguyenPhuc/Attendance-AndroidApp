package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

public class Session {
    public static final int DEFAULT_LATE_AFTER_MINUTES = 15;
    public static final double DEFAULT_RADIUS_METERS = 100;
    public static final String FIELD_ACTIVE = "active";

    private String sessionId;
    private String classId;
    private String shiftId;
    private double latitude;
    private double longitude;
    private double radius = DEFAULT_RADIUS_METERS;      // meters
    private String token;
    private Timestamp startTime;
    // Thời điểm kết thúc theo lịch của ca học; dùng làm ranh giới điểm danh phía server.
    private Timestamp scheduledEndTime;
    private Timestamp endTime;
    private boolean isActive;
    // Nội dung buổi học do giáo viên nhập khi đóng phiên,
    // ví dụ "Buổi 8 - Giao thức TCP/IP".
    private String content;
    // Minutes from startTime within which a check-in counts as on-time;
    // checking in later is marked "late". Default 15.
    private int lateAfterMinutes = DEFAULT_LATE_AFTER_MINUTES;

    public Session() {}

    // Getters & Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getRadius() {
        // Guard old sessions stored without this field (deserialized as 0).
        return radius > 0 ? radius : DEFAULT_RADIUS_METERS;
    }
    public void setRadius(double radius) { this.radius = radius; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getScheduledEndTime() { return scheduledEndTime; }
    public void setScheduledEndTime(Timestamp scheduledEndTime) {
        this.scheduledEndTime = scheduledEndTime;
    }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    @PropertyName(FIELD_ACTIVE)
    public boolean isActive() { return isActive; }

    @PropertyName(FIELD_ACTIVE)
    public void setActive(boolean active) { isActive = active; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getLateAfterMinutes() {
        // Guard old sessions stored without this field (deserialized as 0).
        return lateAfterMinutes > 0 ? lateAfterMinutes : DEFAULT_LATE_AFTER_MINUTES;
    }
    public void setLateAfterMinutes(int lateAfterMinutes) { this.lateAfterMinutes = lateAfterMinutes; }
}
