package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;
import java.util.List;

public class ClassModel {
    private String classId;
    private String className;
    private String teacherId;
    private String teacherName;
    private String startDate;   // YYYY-MM-DD
    private String endDate;     // YYYY-MM-DD
    private List<Integer> schedule; // [2,4] = Mon, Wed
    private String startAt;     // "18:00"
    private String endAt;       // "20:00"
    private String room;
    private String description;
    private int studentCount;
    private Timestamp createdAt;

    public ClassModel() {}

    // Getters & Setters
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public List<Integer> getSchedule() { return schedule; }
    public void setSchedule(List<Integer> schedule) { this.schedule = schedule; }

    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }

    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /**
     * Returns a display string for the schedule e.g. "T2 + T4"
     */
    public String getScheduleDisplay() {
        if (schedule == null || schedule.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < schedule.size(); i++) {
            if (i > 0) sb.append(" + ");
            int dayOfWeek = schedule.get(i);
            sb.append(dayOfWeek == 8 ? "CN" : "T" + dayOfWeek);
        }
        return sb.toString();
    }
}
