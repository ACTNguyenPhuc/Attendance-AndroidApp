package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;

public class Session {
    public static final int DEFAULT_LATE_AFTER_MINUTES = 15;

    private String sessionId;
    private String classId;
    private String shiftId;
    private double latitude;
    private double longitude;
    private double radius;      // meters
    private String token;
    private Timestamp startTime;
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

    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getLateAfterMinutes() {
        // Guard old sessions stored without this field (deserialized as 0).
        return lateAfterMinutes > 0 ? lateAfterMinutes : DEFAULT_LATE_AFTER_MINUTES;
    }
    public void setLateAfterMinutes(int lateAfterMinutes) { this.lateAfterMinutes = lateAfterMinutes; }
}
