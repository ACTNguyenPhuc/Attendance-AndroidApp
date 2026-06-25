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
    private String startAt;     // "18:00" — giờ chung (dữ liệu cũ / fallback)
    private String endAt;       // "20:00" — giờ chung (dữ liệu cũ / fallback)
    // Khung giờ riêng theo từng thứ. Khi có giá trị, đây là nguồn chính thay cho startAt/endAt.
    private List<DaySchedule> daySchedules;
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

    public List<DaySchedule> getDaySchedules() { return daySchedules; }
    public void setDaySchedules(List<DaySchedule> daySchedules) { this.daySchedules = daySchedules; }

    /**
     * Khung giờ của một thứ cụ thể (2..8). Trả về {@code null} nếu lớp không có
     * khung giờ riêng theo ngày (dữ liệu cũ dùng giờ chung).
     */
    public DaySchedule getDayScheduleFor(int dayOfWeek) {
        if (daySchedules == null) return null;
        for (DaySchedule d : daySchedules) {
            if (d != null && d.getDayOfWeek() == dayOfWeek) return d;
        }
        return null;
    }

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

    /**
     * Chuỗi hiển thị lịch học kèm giờ theo TỪNG ngày, vd "T2 07:00-09:30 · T4 13:00-15:00".
     * Nếu lớp dùng dữ liệu cũ (giờ chung) thì trả về "T2 + T4  18:00-20:00".
     */
    public String getScheduleTimeDisplay() {
        if (daySchedules != null && !daySchedules.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (DaySchedule d : daySchedules) {
                if (d == null) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(d.getDayLabel()).append(" ")
                  .append(d.getStartAt()).append("-").append(d.getEndAt());
            }
            return sb.toString();
        }
        // Fallback: dữ liệu cũ với một khung giờ chung.
        String days = getScheduleDisplay();
        if (startAt != null && endAt != null && !startAt.isEmpty() && !endAt.isEmpty()) {
            return days.isEmpty() ? startAt + "-" + endAt : days + "  " + startAt + "-" + endAt;
        }
        return days;
    }
}
