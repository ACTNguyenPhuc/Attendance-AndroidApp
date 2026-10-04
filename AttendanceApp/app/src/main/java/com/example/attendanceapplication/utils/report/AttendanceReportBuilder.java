package com.example.attendanceapplication.utils.report;

import android.os.Handler;
import android.os.Looper;

import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.AttendanceReport;
import com.example.attendanceapplication.models.AttendanceReport.ReportColumn;
import com.example.attendanceapplication.models.AttendanceReport.ReportRow;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Tổng hợp {@link AttendanceReport} của một lớp từ Firestore (chỉ đọc).
 * Đọc lớp trước để xác minh người yêu cầu là giảng viên của lớp, sau đó chạy song song
 * 3 truy vấn shift / sinh viên / điểm danh, rồi áp quy tắc nghiệp vụ trên {@code worker}
 * (không chặn main thread). Kết quả trả về main thread.
 */
public final class AttendanceReportBuilder {

    public interface Callback {
        void onSuccess(AttendanceReport report);
        void onFailure(Exception e);
    }

    /** Người yêu cầu không phải giảng viên của lớp (classes.teacherId). */
    public static class NotClassTeacherException extends Exception {
        public NotClassTeacherException() {
            super("Chỉ giảng viên của lớp mới được xem thống kê này.");
        }
    }

    private final FirebaseRepository repo;
    private final Executor worker;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AttendanceReportBuilder(FirebaseRepository repo, Executor worker) {
        this.repo = repo;
        this.worker = worker;
    }

    public void build(String classId, String requesterUid, Callback callback) {
        repo.getClassById(classId, classModel -> {
            if (classModel == null || requesterUid == null
                    || !requesterUid.equals(classModel.getTeacherId())) {
                callback.onFailure(new NotClassTeacherException());
                return;
            }
            fetchClassData(classId, classModel, callback);
        }, callback::onFailure);
    }

    /** Callback Firestore chạy trên main thread nên các biến đếm dưới đây không cần khóa. */
    private void fetchClassData(String classId, ClassModel classModel, Callback callback) {
        final Parts parts = new Parts();

        Runnable onPartDone = () -> {
            if (parts.failed || --parts.pending > 0) return;
            worker.execute(() -> {
                try {
                    AttendanceReport report = assemble(classModel, parts.shifts, parts.students,
                            parts.attendances, new Date());
                    mainHandler.post(() -> callback.onSuccess(report));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onFailure(e));
                }
            });
        };
        FirebaseRepository.OnFailureListener onError = e -> {
            if (parts.failed) return;
            parts.failed = true;
            callback.onFailure(e);
        };

        repo.getClassShiftsOnce(classId, list -> { parts.shifts = list; onPartDone.run(); }, onError);
        repo.getClassStudents(classId, list -> { parts.students = list; onPartDone.run(); }, onError);
        repo.getClassAttendances(classId, list -> { parts.attendances = list; onPartDone.run(); }, onError);
    }

    /** Gom kết quả của 3 truy vấn song song. */
    private static final class Parts {
        List<Shift> shifts;
        List<User> students;
        List<Attendance> attendances;
        int pending = 3;
        boolean failed;
    }

    /** Áp các quy tắc dữ liệu (mục 3 của đặc tả) — hàm thuần, không I/O. */
    static AttendanceReport assemble(ClassModel classModel, List<Shift> shifts, List<User> students,
                                     List<Attendance> attendances, Date now) {
        // 3.1 Cột buổi học: chỉ lấy shift completed; ongoing đếm riêng để nhắc; bỏ upcoming/cancelled
        List<Shift> completed = new ArrayList<>();
        int ongoing = 0;
        for (Shift s : shifts) {
            if (s == null || s.getShiftId() == null) continue;
            if (Shift.STATUS_COMPLETED.equals(s.getStatus())) completed.add(s);
            else if (Shift.STATUS_ONGOING.equals(s.getStatus())) ongoing++;
        }
        // Sắp theo date, rồi startAt
        Collections.sort(completed, (a, b) -> {
            int c = nz(a.getDate()).compareTo(nz(b.getDate()));
            return c != 0 ? c : nz(a.getStartAt()).compareTo(nz(b.getStartAt()));
        });
        Map<String, Integer> shiftsPerDate = new HashMap<>();
        for (Shift s : completed) {
            Integer cnt = shiftsPerDate.get(nz(s.getDate()));
            shiftsPerDate.put(nz(s.getDate()), cnt == null ? 1 : cnt + 1);
        }
        List<ReportColumn> columns = new ArrayList<>();
        Map<String, Integer> columnIndex = new HashMap<>();
        for (Shift s : completed) {
            // Header chỉ ghi ngày; ≥ 2 buổi cùng ngày (vd học bù) thì thêm giờ để phân biệt
            String label = toDisplayDate(s.getDate());
            if (shiftsPerDate.get(nz(s.getDate())) > 1 && !nz(s.getStartAt()).isEmpty()) {
                label += " (" + s.getStartAt() + ")";
            }
            columnIndex.put(s.getShiftId(), columns.size());
            columns.add(new ReportColumn(s.getShiftId(), s.getDate(), s.getStartAt(), label));
        }

        // 3.3 Trạng thái tốt nhất của mỗi (sinh viên, buổi): present > late > absent
        Map<String, Integer> bestRank = new HashMap<>();
        for (Attendance a : attendances) {
            if (a == null || a.getStudentId() == null || !columnIndex.containsKey(a.getShiftId())) continue;
            int rank = statusRank(a.getStatus());
            String key = a.getStudentId() + "|" + a.getShiftId();
            Integer old = bestRank.get(key);
            if (old == null || rank > old) bestRank.put(key, rank);
        }

        // 3.2 Dòng sinh viên: tách Họ đệm / Tên, sắp xếp kiểu danh sách lớp Việt Nam
        List<StudentName> sorted = new ArrayList<>();
        for (User u : students) {
            if (u == null || u.getUid() == null) continue;
            sorted.add(new StudentName(u));
        }
        Collator collator = Collator.getInstance(new Locale("vi", "VN"));
        Collections.sort(sorted, (a, b) -> {
            int c = collator.compare(a.firstName, b.firstName);
            if (c == 0) c = collator.compare(a.middleName, b.middleName);
            if (c == 0) c = collator.compare(nz(a.user.getStudentCode()), nz(b.user.getStudentCode()));
            return c;
        });

        List<ReportRow> rows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            StudentName s = sorted.get(i);
            char[] marks = new char[columns.size()];
            for (int c = 0; c < columns.size(); c++) {
                Integer rank = bestRank.get(s.user.getUid() + "|" + columns.get(c).getShiftId());
                // Không có bản ghi nào cho buổi đó → tính là vắng
                if (rank == null || rank == RANK_ABSENT) marks[c] = AttendanceReport.MARK_ABSENT;
                else if (rank == RANK_LATE) marks[c] = AttendanceReport.MARK_LATE;
                else marks[c] = AttendanceReport.MARK_PRESENT;
            }
            rows.add(new ReportRow(i + 1, s.user.getUid(), s.user.getStudentCode(),
                    s.middleName, s.firstName, marks));
        }

        String room = nz(classModel.getRoom()).trim();
        String displayName = nz(classModel.getClassName()) + (room.isEmpty() ? "" : " (" + room + ")");
        String dateRange = columns.isEmpty() ? "" : toDisplayDate(columns.get(0).getDate())
                + " - " + toDisplayDate(columns.get(columns.size() - 1).getDate());
        String exportDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(now);

        return new AttendanceReport(classModel.getClassId(), classModel.getClassName(), displayName,
                classModel.getTeacherName(), semesterLabel(classModel.getStartDate()), dateRange,
                exportDate, ongoing, columns, rows);
    }

    private static final int RANK_ABSENT = 0;
    private static final int RANK_LATE = 1;
    private static final int RANK_PRESENT = 2;

    private static int statusRank(String status) {
        if (Attendance.STATUS_PRESENT.equals(status)) return RANK_PRESENT;
        if (Attendance.STATUS_LATE.equals(status)) return RANK_LATE;
        return RANK_ABSENT;
    }

    /**
     * Học kỳ suy ra từ ngày bắt đầu lớp (yyyy-MM-dd): tháng 8–12 → HK1 năm yyyy–yyyy+1;
     * tháng 1–7 → HK2 năm yyyy-1–yyyy.
     */
    static String semesterLabel(String startDate) {
        String unknown = "HỌC KỲ " + AttendanceReport.PLACEHOLDER
                + " NĂM HỌC " + AttendanceReport.PLACEHOLDER;
        if (startDate == null || !startDate.matches("\\d{4}-\\d{2}-\\d{2}")) return unknown;
        int year = Integer.parseInt(startDate.substring(0, 4));
        int month = Integer.parseInt(startDate.substring(5, 7));
        if (month < 1 || month > 12) return unknown;
        return month >= 8
                ? "HỌC KỲ 1 NĂM HỌC " + year + " - " + (year + 1)
                : "HỌC KỲ 2 NĂM HỌC " + (year - 1) + " - " + year;
    }

    /** yyyy-MM-dd → dd/MM/yyyy; giữ nguyên nếu sai định dạng. */
    static String toDisplayDate(String isoDate) {
        if (isoDate == null || !isoDate.matches("\\d{4}-\\d{2}-\\d{2}")) return nz(isoDate);
        return isoDate.substring(8, 10) + "/" + isoDate.substring(5, 7) + "/" + isoDate.substring(0, 4);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** User.name là họ tên đầy đủ: Tên = từ cuối, Họ đệm = các từ còn lại. */
    private static final class StudentName {
        final User user;
        final String middleName;
        final String firstName;

        StudentName(User user) {
            this.user = user;
            String full = nz(user.getName()).trim().replaceAll("\\s+", " ");
            int lastSpace = full.lastIndexOf(' ');
            if (lastSpace < 0) {
                middleName = "";
                firstName = full;
            } else {
                middleName = full.substring(0, lastSpace);
                firstName = full.substring(lastSpace + 1);
            }
        }
    }
}
