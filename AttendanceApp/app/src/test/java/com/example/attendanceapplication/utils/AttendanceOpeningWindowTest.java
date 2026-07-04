package com.example.attendanceapplication.utils;

import com.example.attendanceapplication.models.Shift;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttendanceOpeningWindowTest {

    @Test
    public void allowsOnlyInsideShiftDateAndTime() throws Exception {
        Shift shift = shift("2026-06-01", "07:00", "09:25");

        assertFalse(AttendanceUtils.canOpenAttendanceAt(shift, at("2026-06-01 06:59")));
        assertTrue(AttendanceUtils.canOpenAttendanceAt(shift, at("2026-06-01 07:00")));
        assertTrue(AttendanceUtils.canOpenAttendanceAt(shift, at("2026-06-01 08:00")));
        assertFalse(AttendanceUtils.canOpenAttendanceAt(shift, at("2026-06-01 09:25")));
        assertFalse(AttendanceUtils.canOpenAttendanceAt(shift, at("2026-06-02 08:00")));
    }

    @Test
    public void rejectsCompletedCancelledOrAlreadyOpenedShift() throws Exception {
        Date duringShift = at("2026-06-01 08:00");

        Shift completed = shift("2026-06-01", "07:00", "09:25");
        completed.setStatus(Shift.STATUS_COMPLETED);
        assertFalse(AttendanceUtils.canOpenAttendanceAt(completed, duringShift));

        Shift cancelled = shift("2026-06-01", "07:00", "09:25");
        cancelled.setStatus(Shift.STATUS_CANCELLED);
        assertFalse(AttendanceUtils.canOpenAttendanceAt(cancelled, duringShift));

        Shift opened = shift("2026-06-01", "07:00", "09:25");
        opened.setAttendanceOpened(true);
        assertFalse(AttendanceUtils.canOpenAttendanceAt(opened, duringShift));
    }

    private Shift shift(String date, String startAt, String endAt) {
        Shift shift = new Shift();
        shift.setStatus(Shift.STATUS_UPCOMING);
        shift.setDate(date);
        shift.setStartAt(startAt);
        shift.setEndAt(endAt);
        return shift;
    }

    private Date at(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(value);
    }
}
