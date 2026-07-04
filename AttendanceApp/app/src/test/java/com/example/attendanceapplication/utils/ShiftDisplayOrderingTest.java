package com.example.attendanceapplication.utils;

import com.example.attendanceapplication.models.Shift;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShiftDisplayOrderingTest {

    @Test
    public void inProgress_requiresOngoingStatusAndOpenedAttendance() {
        Shift shift = shift("active", "2026-06-03", "09:35");

        shift.setStatus(Shift.STATUS_ONGOING);
        assertFalse(AttendanceUtils.isAttendanceInProgress(shift));

        shift.setAttendanceOpened(true);
        assertTrue(AttendanceUtils.isAttendanceInProgress(shift));

        shift.setStatus(Shift.STATUS_COMPLETED);
        assertFalse(AttendanceUtils.isAttendanceInProgress(shift));
    }

    @Test
    public void sortShiftsActiveFirst_thenChronologically() {
        Shift later = shift("later", "2026-06-10", "13:00");
        Shift earlier = shift("earlier", "2026-06-01", "07:00");
        Shift active = shift("active", "2026-06-05", "09:35");
        active.setStatus(Shift.STATUS_ONGOING);
        active.setAttendanceOpened(true);

        List<Shift> shifts = new ArrayList<>(Arrays.asList(later, active, earlier));
        AttendanceUtils.sortShiftsActiveFirst(shifts);

        assertEquals("active", shifts.get(0).getShiftId());
        assertEquals("earlier", shifts.get(1).getShiftId());
        assertEquals("later", shifts.get(2).getShiftId());
    }

    private Shift shift(String id, String date, String startAt) {
        Shift shift = new Shift();
        shift.setShiftId(id);
        shift.setStatus(Shift.STATUS_UPCOMING);
        shift.setDate(date);
        shift.setStartAt(startAt);
        return shift;
    }
}
