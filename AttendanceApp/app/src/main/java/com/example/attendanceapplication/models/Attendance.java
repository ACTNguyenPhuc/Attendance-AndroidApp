package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class Attendance {
    public static final String STATUS_PRESENT = "present";
    public static final String STATUS_LATE = "late";
    public static final String STATUS_ABSENT = "absent";

    // Kết quả đối chiếu BSSID Wi-Fi: chỉ dùng để cảnh báo, không chặn điểm danh.
    public static final String BSSID_VALID = "valid";
    public static final String BSSID_WARNING = "warning";

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
    // BSSID Wi-Fi giảng viên lúc mở phiên và BSSID thực tế của máy sinh viên
    // lúc điểm danh (null nếu không đọc được).
    private String sessionBssid;
    private String deviceBssid;
    private String bssidStatus;
    // Lý do cảnh báo, ví dụ "Không kết nối Wi-Fi" (null nếu hợp lệ).
    private String bssidNote;

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

    public String getSessionBssid() { return sessionBssid; }
    public void setSessionBssid(String sessionBssid) { this.sessionBssid = sessionBssid; }

    public String getDeviceBssid() { return deviceBssid; }
    public void setDeviceBssid(String deviceBssid) { this.deviceBssid = deviceBssid; }

    public String getBssidStatus() { return bssidStatus; }
    public void setBssidStatus(String bssidStatus) { this.bssidStatus = bssidStatus; }

    public String getBssidNote() { return bssidNote; }
    public void setBssidNote(String bssidNote) { this.bssidNote = bssidNote; }

    /** Bản ghi cũ (trước khi có kiểm tra BSSID) không có trạng thái → không cảnh báo. */
    @Exclude
    public boolean hasBssidWarning() { return BSSID_WARNING.equals(bssidStatus); }
}
