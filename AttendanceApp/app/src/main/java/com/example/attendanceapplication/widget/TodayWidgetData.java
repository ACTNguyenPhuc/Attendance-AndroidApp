package com.example.attendanceapplication.widget;

import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dữ liệu các buổi học hôm nay cho widget, đã tải xong từ Firestore. */
public class TodayWidgetData {
    public boolean teacher;
    public final List<Shift> shifts = new ArrayList<>();
    /** Giảng viên: classId -> sĩ số (enrollment đang active). */
    public final Map<String, Integer> rosterByClass = new HashMap<>();
    /** Giảng viên: shiftId -> số lượt điểm danh. */
    public final Map<String, Integer> presentByShift = new HashMap<>();
    /** Sinh viên: shiftId -> bản ghi điểm danh của chính mình. */
    public final Map<String, Attendance> mineByShift = new HashMap<>();
}
