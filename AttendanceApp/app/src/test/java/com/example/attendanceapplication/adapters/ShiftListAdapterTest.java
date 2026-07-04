package com.example.attendanceapplication.adapters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.attendanceapplication.models.Shift;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShiftListAdapterTest {

    @Test
    public void isDeletable_allowsUpcomingShiftBeforeAttendanceOpens() {
        Shift shift = shiftWithStatus(Shift.STATUS_UPCOMING, false);

        assertTrue(ShiftListAdapter.isDeletable(shift));
    }

    @Test
    public void isDeletable_rejectsNonUpcomingShift() {
        Shift shift = shiftWithStatus(Shift.STATUS_ONGOING, false);

        assertFalse(ShiftListAdapter.isDeletable(shift));
    }

    @Test
    public void isDeletable_rejectsShiftWithOpenedAttendance() {
        Shift shift = shiftWithStatus(Shift.STATUS_UPCOMING, true);

        assertFalse(ShiftListAdapter.isDeletable(shift));
    }

    @Test
    public void canOpenAttendanceAt_rejectsBeforeShift() throws Exception {
        Shift shift = scheduledShift("2026-06-01", "07:00", "09:25");

        assertFalse(ShiftListAdapter.canOpenAttendanceAt(
                shift, parseDateTime("2026-06-01 06:59")));
    }

    @Test
    public void canOpenAttendanceAt_allowsDuringShift() throws Exception {
        Shift shift = scheduledShift("2026-06-01", "07:00", "09:25");

        assertTrue(ShiftListAdapter.canOpenAttendanceAt(
                shift, parseDateTime("2026-06-01 08:00")));
    }

    @Test
    public void canOpenAttendanceAt_rejectsAtEndAndOnOtherDate() throws Exception {
        Shift shift = scheduledShift("2026-06-01", "07:00", "09:25");

        assertFalse(ShiftListAdapter.canOpenAttendanceAt(
                shift, parseDateTime("2026-06-01 09:25")));
        assertFalse(ShiftListAdapter.canOpenAttendanceAt(
                shift, parseDateTime("2026-06-02 08:00")));
    }

    private Shift shiftWithStatus(String status, boolean attendanceOpened) {
        Shift shift = new Shift();
        shift.setStatus(status);
        shift.setAttendanceOpened(attendanceOpened);
        return shift;
    }

    private Shift scheduledShift(String date, String startAt, String endAt) {
        Shift shift = shiftWithStatus(Shift.STATUS_UPCOMING, false);
        shift.setDate(date);
        shift.setStartAt(startAt);
        shift.setEndAt(endAt);
        return shift;
    }

    private Date parseDateTime(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(value);
    }
}
