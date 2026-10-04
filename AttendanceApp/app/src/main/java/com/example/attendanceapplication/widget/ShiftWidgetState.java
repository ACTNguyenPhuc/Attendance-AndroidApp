package com.example.attendanceapplication.widget;

import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Nội dung một card buổi học trên widget màn hình chính, tính sẵn theo vai trò.
 * Ba trạng thái chính: đã hoàn tất ({@link Kind#DONE}), đến giờ / đang điểm danh
 * ({@link Kind#LIVE}) và sắp tới ({@link Kind#UPCOMING}).
 * Lớp thuần Java để kiểm thử được mà không cần thiết bị.
 */
public final class ShiftWidgetState {

    public enum Kind { DONE, LIVE, UPCOMING, CANCELLED }

    /** Màu + icon của dòng tiêu đề trạng thái. */
    public enum Tone { GREEN, BLUE, GRAY, RED, ORANGE }

    /** Hành động khi bấm card hoặc nút. */
    public enum Action { NONE, OPEN_APP, OPEN_SESSION, VIEW_RESULT, SCAN, SHIFT_DETAIL }

    public Kind kind;
    public Tone headerTone;
    public String header;
    /** Khoảng giờ hiển thị góc phải tiêu đề; chỉ có ở trạng thái LIVE. */
    public String headerRight;
    public String statusLine;
    public Tone statusTone;
    /** null = không có nút. */
    public String buttonText;
    public Action buttonAction = Action.NONE;
    public Action cardAction = Action.OPEN_APP;
    /** Dòng chú thích dưới nút; null = ẩn. */
    public String footer;

    private ShiftWidgetState() {}

    public static ShiftWidgetState forTeacher(Shift s, Date now, int rosterSize, int presentCount) {
        ShiftWidgetState st = new ShiftWidgetState();
        String start = text(s.getStartAt());
        String end = text(s.getEndAt());
        boolean everOpened = s.getAttendanceSessionId() != null;

        if (Shift.STATUS_CANCELLED.equals(s.getStatus())) {
            return cancelled(st);
        }
        if (isInProgress(s)) {
            st.kind = Kind.LIVE;
            st.headerTone = Tone.BLUE;
            st.header = "Đang điểm danh";
            st.headerRight = start + " – " + end;
            st.statusLine = presentCount + "/" + rosterSize + " sinh viên có mặt · Phiên đang mở";
            st.statusTone = Tone.BLUE;
            st.buttonText = "Xem điểm danh";
            st.buttonAction = Action.OPEN_SESSION;
            st.cardAction = Action.OPEN_SESSION;
            st.footer = "Phiên sẽ kết thúc lúc " + end;
            return st;
        }
        Phase phase = phase(s, now);
        if (Shift.STATUS_COMPLETED.equals(s.getStatus()) || phase == Phase.AFTER) {
            st.kind = Kind.DONE;
            if (everOpened) {
                st.headerTone = Tone.GREEN;
                st.header = "Đã hoàn tất";
                st.statusLine = presentCount + "/" + rosterSize + " sinh viên có mặt";
                st.statusTone = Tone.GREEN;
                st.cardAction = Action.VIEW_RESULT;
            } else {
                st.headerTone = Tone.GRAY;
                st.header = "Đã kết thúc";
                st.statusLine = "Không mở điểm danh";
                st.statusTone = Tone.GRAY;
            }
            return st;
        }
        if (phase == Phase.DURING) {
            st.kind = Kind.LIVE;
            st.headerTone = Tone.BLUE;
            st.header = "Đến giờ mở";
            st.headerRight = start + " – " + end;
            st.statusLine = rosterSize + " sinh viên · Chưa mở phiên";
            st.statusTone = Tone.BLUE;
            st.buttonText = "Mở điểm danh";
            st.buttonAction = Action.OPEN_SESSION;
            st.cardAction = Action.OPEN_SESSION;
            st.footer = "Phiên sẽ kết thúc lúc " + end;
            return st;
        }
        st.kind = Kind.UPCOMING;
        st.headerTone = Tone.GRAY;
        st.header = "Sắp tới";
        st.statusLine = "Có thể mở điểm danh lúc " + start;
        st.statusTone = Tone.GRAY;
        return st;
    }

    /** @param mine bản ghi điểm danh của sinh viên trong buổi này, null nếu chưa có. */
    public static ShiftWidgetState forStudent(Shift s, Date now, Attendance mine,
                                              String checkinTimeText) {
        ShiftWidgetState st = new ShiftWidgetState();
        String start = text(s.getStartAt());
        String end = text(s.getEndAt());
        st.cardAction = Action.SHIFT_DETAIL;

        if (Shift.STATUS_CANCELLED.equals(s.getStatus())) {
            return cancelled(st);
        }
        if (mine != null) {
            boolean late = Attendance.STATUS_LATE.equals(mine.getStatus());
            st.kind = Kind.DONE;
            st.headerTone = Tone.GREEN;
            st.header = "Đã điểm danh";
            st.statusLine = (late ? "Đi muộn" : "Có mặt")
                    + (checkinTimeText != null ? " · Ghi nhận lúc " + checkinTimeText : "")
                    + (mine.hasBssidWarning() ? " · ⚠ Wi-Fi" : "");
            st.statusTone = late ? Tone.ORANGE : Tone.GREEN;
            return st;
        }
        if (isInProgress(s)) {
            st.kind = Kind.LIVE;
            st.headerTone = Tone.BLUE;
            st.header = "Đang điểm danh";
            st.headerRight = start + " – " + end;
            st.statusLine = "Phiên đang mở · Bạn chưa điểm danh";
            st.statusTone = Tone.BLUE;
            st.buttonText = "Điểm danh";
            st.buttonAction = Action.SCAN;
            st.footer = "Kết thúc lúc " + end;
            return st;
        }
        Phase phase = phase(s, now);
        if (Shift.STATUS_COMPLETED.equals(s.getStatus()) || phase == Phase.AFTER) {
            st.kind = Kind.DONE;
            st.headerTone = Tone.GRAY;
            st.header = "Đã kết thúc";
            if (s.getAttendanceSessionId() != null) {
                st.statusLine = "Vắng";
                st.statusTone = Tone.RED;
            } else {
                st.statusLine = "Không mở điểm danh";
                st.statusTone = Tone.GRAY;
            }
            return st;
        }
        if (phase == Phase.DURING) {
            st.kind = Kind.LIVE;
            st.headerTone = Tone.BLUE;
            st.header = "Đang diễn ra";
            st.headerRight = start + " – " + end;
            st.statusLine = "Giảng viên chưa mở phiên điểm danh";
            st.statusTone = Tone.GRAY;
            st.footer = "Kết thúc lúc " + end;
            return st;
        }
        st.kind = Kind.UPCOMING;
        st.headerTone = Tone.GRAY;
        st.header = "Sắp tới";
        st.statusLine = "Chưa đến giờ điểm danh";
        st.statusTone = Tone.GRAY;
        return st;
    }

    private static ShiftWidgetState cancelled(ShiftWidgetState st) {
        st.kind = Kind.CANCELLED;
        st.headerTone = Tone.RED;
        st.header = "Đã hủy";
        st.statusLine = "Buổi học đã bị hủy";
        st.statusTone = Tone.GRAY;
        return st;
    }

    /** Phiên đang mở; gồm cả phiên điểm danh bù mở sau giờ kết thúc ca. */
    private static boolean isInProgress(Shift s) {
        return s.isAttendanceOpened() && Shift.STATUS_ONGOING.equals(s.getStatus());
    }

    enum Phase { BEFORE, DURING, AFTER }

    static Phase phase(Shift s, Date now) {
        Date start = parse(s.getDate(), s.getStartAt());
        Date end = parse(s.getDate(), s.getEndAt());
        if (start == null || end == null || now.before(start)) return Phase.BEFORE;
        return now.before(end) ? Phase.DURING : Phase.AFTER;
    }

    /** "yyyy-MM-dd" + "HH:mm" theo múi giờ của máy; null nếu sai định dạng. */
    public static Date parse(String date, String time) {
        if (date == null || time == null) return null;
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            parser.setLenient(false);
            return parser.parse(date + " " + time);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? "--:--" : value.trim();
    }
}
