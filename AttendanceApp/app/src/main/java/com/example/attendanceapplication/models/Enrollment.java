package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;

public class Enrollment {
    private String studentId;
    private String classId;
    private Timestamp joinedAt;
    private String status; // active / inactive

    public Enrollment() {}

    public Enrollment(String studentId, String classId) {
        this.studentId = studentId;
        this.classId = classId;
        this.status = "active";
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
