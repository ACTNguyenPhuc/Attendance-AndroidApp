package com.example.attendanceapplication.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.example.attendanceapplication.models.Shift;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RoomConflictCheckerTest {

    @Test
    public void findsSameDateRoomAndOverlappingTime() {
        Shift requested = shift("2026-07-06", "A101", "08:00", "10:00", "NEW");
        Shift existing = shift("2026-07-06", " a101 ", "09:30", "11:00", "OLD");

        RoomConflictChecker.Conflict conflict = RoomConflictChecker.findFirstConflict(
                Collections.singletonList(requested), Collections.singletonList(existing));

        assertNotNull(conflict);
        assertSame(requested, conflict.getNewShift());
        assertSame(existing, conflict.getExistingShift());
    }

    @Test
    public void acceptsAdjacentTimeRanges() {
        Shift requested = shift("2026-07-06", "A101", "10:00", "11:00", "NEW");
        Shift existing = shift("2026-07-06", "A101", "08:00", "10:00", "OLD");

        assertNull(RoomConflictChecker.findFirstConflict(
                Collections.singletonList(requested), Collections.singletonList(existing)));
    }

    @Test
    public void checksEveryNewShiftAgainstEveryExistingShift() {
        Shift firstNew = shift("2026-07-06", "A101", "08:00", "10:00", "NEW");
        Shift conflictingNew = shift("2026-07-08", "B202", "13:00", "15:00", "NEW");
        Shift unrelatedExisting = shift("2026-07-06", "C303", "08:00", "10:00", "A");
        Shift conflictingExisting = shift("2026-07-08", "B202", "14:00", "16:00", "B");

        RoomConflictChecker.Conflict conflict = RoomConflictChecker.findFirstConflict(
                Arrays.asList(firstNew, conflictingNew),
                Arrays.asList(unrelatedExisting, conflictingExisting));

        assertNotNull(conflict);
        assertSame(conflictingExisting, conflict.getExistingShift());
    }

    @Test
    public void acceptsDifferentDateOrRoom() {
        Shift requested = shift("2026-07-06", "A101", "08:00", "10:00", "NEW");
        Shift otherDate = shift("2026-07-07", "A101", "08:00", "10:00", "A");
        Shift otherRoom = shift("2026-07-06", "A102", "08:00", "10:00", "B");

        assertNull(RoomConflictChecker.findFirstConflict(
                Collections.singletonList(requested), Arrays.asList(otherDate, otherRoom)));
    }

    private Shift shift(String date, String room, String startAt, String endAt, String classId) {
        Shift shift = new Shift();
        shift.setDate(date);
        shift.setRoom(room);
        shift.setStartAt(startAt);
        shift.setEndAt(endAt);
        shift.setClassId(classId);
        return shift;
    }
}
