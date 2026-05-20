package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;

public class Attendance {
    public static final String STATUS_PRESENT = "present";
    public static final String STATUS_LATE = "late";
    public static final String STATUS_ABSENT = "absent";

    private String attendanceId;
    private String studentId;
    private String studentName;
    private String studentCode;
    private String sessionId;
    private String shiftId;
    private String classId;
    private double latitude;
    private double longitude;
    private double distance;
    private Timestamp checkinTime;
    private String status;
    private String selfieUrl;
    private boolean faceVerified;
    private String deviceId;

    public Attendance() {}

    // Getters & Setters
    public String getAttendanceId() { return attendanceId; }
    public void setAttendanceId(String attendanceId) { this.attendanceId = attendanceId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    public Timestamp getCheckinTime() { return checkinTime; }
    public void setCheckinTime(Timestamp checkinTime) { this.checkinTime = checkinTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSelfieUrl() { return selfieUrl; }
    public void setSelfieUrl(String selfieUrl) { this.selfieUrl = selfieUrl; }

    public boolean isFaceVerified() { return faceVerified; }
    public void setFaceVerified(boolean faceVerified) { this.faceVerified = faceVerified; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
