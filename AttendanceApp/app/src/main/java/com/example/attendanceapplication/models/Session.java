package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;

public class Session {
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
}
