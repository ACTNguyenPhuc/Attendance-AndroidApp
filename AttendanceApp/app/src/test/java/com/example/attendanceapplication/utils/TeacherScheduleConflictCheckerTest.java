package com.example.attendanceapplication.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.example.attendanceapplication.models.Shift;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class TeacherScheduleConflictCheckerTest {

    @Test
    public void findsSameTeacherDateAndOverlappingTime() {
        Shift requested = shift("teacher-1", "2026-07-06", "08:00", "10:00", "NEW");
        Shift existing = shift("teacher-1", "2026-07-06", "09:30", "11:00", "OLD");

        TeacherScheduleConflictChecker.Conflict conflict =
                TeacherScheduleConflictChecker.findFirstConflict(
                        Collections.singletonList(requested),
                        Collections.singletonList(existing));

        assertNotNull(conflict);
        assertSame(requested, conflict.getNewShift());
        assertSame(existing, conflict.getExistingShift());
    }

    @Test
    public void acceptsDifferentTeacherOrDate() {
        Shift requested = shift("teacher-1", "2026-07-06", "08:00", "10:00", "NEW");
        Shift otherTeacher = shift("teacher-2", "2026-07-06", "08:30", "09:30", "A");
        Shift otherDate = shift("teacher-1", "2026-07-07", "08:30", "09:30", "B");

        assertNull(TeacherScheduleConflictChecker.findFirstConflict(
                Collections.singletonList(requested), Arrays.asList(otherTeacher, otherDate)));
    }

    @Test
    public void acceptsAdjacentTimeRanges() {
        Shift requested = shift("teacher-1", "2026-07-06", "10:00", "11:00", "NEW");
        Shift existing = shift("teacher-1", "2026-07-06", "08:00", "10:00", "OLD");

        assertNull(TeacherScheduleConflictChecker.findFirstConflict(
                Collections.singletonList(requested), Collections.singletonList(existing)));
    }

    @Test
    public void checksEveryNewShiftAgainstEveryExistingShift() {
        Shift firstNew = shift("teacher-1", "2026-07-06", "08:00", "10:00", "NEW");
        Shift conflictingNew = shift("teacher-1", "2026-07-08", "13:00", "15:00", "NEW");
        Shift unrelated = shift("teacher-2", "2026-07-06", "08:00", "10:00", "A");
        Shift conflicting = shift("teacher-1", "2026-07-08", "14:00", "16:00", "B");

        TeacherScheduleConflictChecker.Conflict conflict =
                TeacherScheduleConflictChecker.findFirstConflict(
                        Arrays.asList(firstNew, conflictingNew),
                        Arrays.asList(unrelated, conflicting));

        assertNotNull(conflict);
        assertSame(conflicting, conflict.getExistingShift());
    }

    private Shift shift(String teacherId, String date, String startAt,
                        String endAt, String classId) {
        Shift shift = new Shift();
        shift.setTeacherName(teacherId);
        shift.setDate(date);
        shift.setStartAt(startAt);
        shift.setEndAt(endAt);
        shift.setClassId(classId);
        return shift;
    }
}
