package com.example.attendanceapplication.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.widget.ShiftWidgetState.Action;
import com.example.attendanceapplication.widget.ShiftWidgetState.Kind;

import org.junit.Test;

import java.util.Date;

public class ShiftWidgetStateTest {

    private static final Date AT_0905 = ShiftWidgetState.parse("2026-10-04", "09:05");

    @Test
    public void teacherCompletedShiftShowsPresentCount() {
        Shift s = shift("07:30", "09:00", Shift.STATUS_COMPLETED, false, "sess1");
        ShiftWidgetState st = ShiftWidgetState.forTeacher(s, AT_0905, 35, 32);
        assertEquals(Kind.DONE, st.kind);
        assertEquals("Đã hoàn tất", st.header);
        assertEquals("32/35 sinh viên có mặt", st.statusLine);
        assertEquals(Action.VIEW_RESULT, st.cardAction);
        assertNull(st.buttonText);
    }

    @Test
    public void teacherDuringShiftCanOpenAttendance() {
        Shift s = shift("09:00", "10:30", Shift.STATUS_UPCOMING, false, null);
        ShiftWidgetState st = ShiftWidgetState.forTeacher(s, AT_0905, 35, 0);
        assertEquals(Kind.LIVE, st.kind);
        assertEquals("Đến giờ mở", st.header);
        assertEquals("09:00 – 10:30", st.headerRight);
        assertEquals("35 sinh viên · Chưa mở phiên", st.statusLine);
        assertEquals("Mở điểm danh", st.buttonText);
        assertEquals(Action.OPEN_SESSION, st.buttonAction);
        assertEquals("Phiên sẽ kết thúc lúc 10:30", st.footer);
    }

    @Test
    public void teacherUpcomingShowsOpeningTime() {
        Shift s = shift("13:00", "14:30", Shift.STATUS_UPCOMING, false, null);
        ShiftWidgetState st = ShiftWidgetState.forTeacher(s, AT_0905, 35, 0);
        assertEquals(Kind.UPCOMING, st.kind);
        assertEquals("Có thể mở điểm danh lúc 13:00", st.statusLine);
    }

    @Test
    public void teacherPastShiftNeverOpenedIsEndedWithoutAttendance() {
        Shift s = shift("07:00", "08:00", Shift.STATUS_UPCOMING, false, null);
        ShiftWidgetState st = ShiftWidgetState.forTeacher(s, AT_0905, 35, 0);
        assertEquals(Kind.DONE, st.kind);
        assertEquals("Không mở điểm danh", st.statusLine);
        assertEquals(Action.OPEN_APP, st.cardAction);
    }

    @Test
    public void studentOpenSessionShowsScanButton() {
        Shift s = shift("09:00", "10:30", Shift.STATUS_ONGOING, true, "sess2");
        ShiftWidgetState st = ShiftWidgetState.forStudent(s, AT_0905, null, null);
        assertEquals(Kind.LIVE, st.kind);
        assertEquals("Đang điểm danh", st.header);
        assertEquals("Phiên đang mở · Bạn chưa điểm danh", st.statusLine);
        assertEquals("Điểm danh", st.buttonText);
        assertEquals(Action.SCAN, st.buttonAction);
    }

    @Test
    public void studentAttendedShowsCheckinTime() {
        Shift s = shift("07:30", "09:00", Shift.STATUS_COMPLETED, false, "sess1");
        Attendance a = new Attendance();
        a.setStatus(Attendance.STATUS_PRESENT);
        ShiftWidgetState st = ShiftWidgetState.forStudent(s, AT_0905, a, "07:42");
        assertEquals(Kind.DONE, st.kind);
        assertEquals("Đã điểm danh", st.header);
        assertEquals("Có mặt · Ghi nhận lúc 07:42", st.statusLine);
    }

    @Test
    public void studentMissedOpenedShiftIsAbsent() {
        Shift s = shift("07:30", "09:00", Shift.STATUS_COMPLETED, false, "sess1");
        ShiftWidgetState st = ShiftWidgetState.forStudent(s, AT_0905, null, null);
        assertEquals("Vắng", st.statusLine);
    }

    @Test
    public void studentUpcoming() {
        Shift s = shift("13:00", "14:30", Shift.STATUS_UPCOMING, false, null);
        ShiftWidgetState st = ShiftWidgetState.forStudent(s, AT_0905, null, null);
        assertEquals(Kind.UPCOMING, st.kind);
        assertEquals("Chưa đến giờ điểm danh", st.statusLine);
    }

    @Test
    public void makeupSessionAfterEndStillLive() {
        Shift s = shift("07:00", "08:00", Shift.STATUS_ONGOING, true, "sess3");
        ShiftWidgetState st = ShiftWidgetState.forTeacher(s, AT_0905, 30, 4);
        assertEquals(Kind.LIVE, st.kind);
        assertEquals("Đang điểm danh", st.header);
    }

    private static Shift shift(String start, String end, String status, boolean opened,
                               String sessionId) {
        Shift s = new Shift();
        s.setShiftId("s-" + start);
        s.setDate("2026-10-04");
        s.setStartAt(start);
        s.setEndAt(end);
        s.setStatus(status);
        s.setAttendanceOpened(opened);
        s.setAttendanceSessionId(sessionId);
        return s;
    }
}
