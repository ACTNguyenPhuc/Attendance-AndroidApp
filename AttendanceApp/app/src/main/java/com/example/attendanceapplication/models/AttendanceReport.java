package com.example.attendanceapplication.models;

import java.util.Collections;
import java.util.List;

/**
 * Bảng thống kê điểm danh của một lớp học phần — model thuần (không phụ thuộc Android UI),
 * dùng chung cho bản xem trước (HTML) và file Excel để hai bên luôn khớp số liệu.
 * Mọi con số được tính sẵn trong constructor.
 */
public class AttendanceReport {

    /** Ngưỡng chuyên cần — cùng ngưỡng với StatsTabFragment (≥ 80% = tốt). */
    public static final double GOOD_RATE_THRESHOLD = 0.8;

    public static final char MARK_PRESENT = 'x';
    public static final char MARK_LATE    = 'M';
    public static final char MARK_ABSENT  = 'V';

    /** Giá trị cho các trường không có dữ liệu (Khoa, Số TC, Khóa) — giảng viên điền tay. */
    public static final String PLACEHOLDER = "……";

    // Thông tin lớp (header)
    private final String classId;
    private final String className;
    private final String classDisplayName;   // className (+ room trong ngoặc)
    private final String teacherName;
    private final String semesterLabel;      // "HỌC KỲ 1 NĂM HỌC 2025 - 2026"
    private final String dateRange;          // "12/01/2026 - 30/03/2026"
    private final String exportDate;         // "04/10/2026"
    private final int ongoingShiftCount;     // buổi đang điểm danh, chưa được tính

    private final List<ReportColumn> columns;
    private final List<ReportRow> rows;

    // Tổng theo cột (mỗi buổi) và tổng cả lớp
    private final int[] presentPerColumn;    // x + M
    private final int[] absentPerColumn;     // V
    private final int totalPresent;          // tổng (x + M) cả lớp
    private final int totalAbsent;           // tổng V cả lớp
    private final int totalCells;
    private final Double classRate;          // null nếu không có ô nào

    public AttendanceReport(String classId, String className, String classDisplayName,
                            String teacherName, String semesterLabel, String dateRange,
                            String exportDate, int ongoingShiftCount,
                            List<ReportColumn> columns, List<ReportRow> rows) {
        this.classId = classId;
        this.className = className;
        this.classDisplayName = classDisplayName;
        this.teacherName = teacherName;
        this.semesterLabel = semesterLabel;
        this.dateRange = dateRange;
        this.exportDate = exportDate;
        this.ongoingShiftCount = ongoingShiftCount;
        this.columns = Collections.unmodifiableList(columns);
        this.rows = Collections.unmodifiableList(rows);

        int n = columns.size();
        presentPerColumn = new int[n];
        absentPerColumn = new int[n];
        int present = 0, absent = 0;
        for (ReportRow row : rows) {
            for (int c = 0; c < n; c++) {
                char m = row.getMarks()[c];
                if (m == MARK_ABSENT) { absentPerColumn[c]++; absent++; }
                else { presentPerColumn[c]++; present++; }   // x và M đều tính là có mặt
            }
        }
        totalPresent = present;
        totalAbsent = absent;
        totalCells = n * rows.size();
        classRate = totalCells == 0 ? null : (double) totalPresent / totalCells;
    }

    /** Không có buổi completed hoặc không có sinh viên → không có gì để xuất. */
    public boolean isEmpty() { return columns.isEmpty() || rows.isEmpty(); }

    public static boolean isLowRate(Double rate) {
        return rate != null && rate < GOOD_RATE_THRESHOLD;
    }

    public String getClassId() { return classId; }
    public String getClassName() { return className; }
    public String getClassDisplayName() { return classDisplayName; }
    public String getTeacherName() { return teacherName; }
    public String getSemesterLabel() { return semesterLabel; }
    public String getDateRange() { return dateRange; }
    public String getExportDate() { return exportDate; }
    public int getOngoingShiftCount() { return ongoingShiftCount; }
    public List<ReportColumn> getColumns() { return columns; }
    public List<ReportRow> getRows() { return rows; }
    public int getPresentForColumn(int col) { return presentPerColumn[col]; }
    public int getAbsentForColumn(int col) { return absentPerColumn[col]; }
    public int getTotalPresent() { return totalPresent; }
    public int getTotalAbsent() { return totalAbsent; }
    public int getTotalCells() { return totalCells; }
    public Double getClassRate() { return classRate; }

    /** Một cột = một buổi học đã điểm danh xong. */
    public static class ReportColumn {
        private final String shiftId;
        private final String date;      // yyyy-MM-dd
        private final String startAt;   // HH:mm
        private final String label;     // dd/MM/yyyy hoặc dd/MM/yyyy (HH:mm)

        public ReportColumn(String shiftId, String date, String startAt, String label) {
            this.shiftId = shiftId;
            this.date = date;
            this.startAt = startAt;
            this.label = label;
        }

        public String getShiftId() { return shiftId; }
        public String getDate() { return date; }
        public String getStartAt() { return startAt; }
        public String getLabel() { return label; }
    }

    /** Một dòng = một sinh viên; số liệu tổng hợp tính sẵn từ {@code marks}. */
    public static class ReportRow {
        private final int stt;
        private final String studentId;
        private final String studentCode;
        private final String middleName;  // Họ đệm
        private final String firstName;   // Tên
        private final char[] marks;
        private final int presentCount;
        private final int lateCount;
        private final int absentCount;
        private final Double rate;        // null nếu số buổi = 0

        public ReportRow(int stt, String studentId, String studentCode,
                         String middleName, String firstName, char[] marks) {
            this.stt = stt;
            this.studentId = studentId;
            this.studentCode = studentCode;
            this.middleName = middleName;
            this.firstName = firstName;
            this.marks = marks;
            int p = 0, l = 0, a = 0;
            for (char m : marks) {
                if (m == MARK_PRESENT) p++;
                else if (m == MARK_LATE) l++;
                else a++;
            }
            presentCount = p;
            lateCount = l;
            absentCount = a;
            // Tỷ lệ chuyên cần = (Có mặt + Đi muộn) / Số buổi
            rate = marks.length == 0 ? null : (double) (p + l) / marks.length;
        }

        public int getStt() { return stt; }
        public String getStudentId() { return studentId; }
        public String getStudentCode() { return studentCode; }
        public String getMiddleName() { return middleName; }
        public String getFirstName() { return firstName; }
        public char[] getMarks() { return marks; }
        public int getPresentCount() { return presentCount; }
        public int getLateCount() { return lateCount; }
        public int getAbsentCount() { return absentCount; }
        public Double getRate() { return rate; }
    }
}
