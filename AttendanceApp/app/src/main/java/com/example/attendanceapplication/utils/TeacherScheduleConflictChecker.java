package com.example.attendanceapplication.utils;

import com.example.attendanceapplication.models.Shift;

import java.util.List;

/** Phát hiện một giảng viên được phân công hai ca học giao nhau. */
public final class TeacherScheduleConflictChecker {

    private TeacherScheduleConflictChecker() {}

    public static Conflict findFirstConflict(List<Shift> newShifts,
                                             List<Shift> existingShifts) {
        if (newShifts == null || existingShifts == null) return null;

        for (Shift newShift : newShifts) {
            if (newShift == null) continue;
            for (Shift existingShift : existingShifts) {
                if (existingShift == null) continue;
                if (!sameTeacherId(newShift.getTeacherName(), existingShift.getTeacherName())) {
                    continue;
                }
                if (!sameDate(newShift.getDate(), existingShift.getDate())) continue;
                if (RoomConflictChecker.timesOverlap(
                        newShift.getStartAt(), newShift.getEndAt(),
                        existingShift.getStartAt(), existingShift.getEndAt())) {
                    return new Conflict(newShift, existingShift);
                }
            }
        }
        return null;
    }

    private static boolean sameTeacherId(String first, String second) {
        String firstId = normalize(first);
        String secondId = normalize(second);
        return !firstId.isEmpty() && firstId.equals(secondId);
    }

    private static boolean sameDate(String first, String second) {
        return first != null && first.equals(second);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Conflict {
        private final Shift newShift;
        private final Shift existingShift;

        private Conflict(Shift newShift, Shift existingShift) {
            this.newShift = newShift;
            this.existingShift = existingShift;
        }

        public Shift getNewShift() {
            return newShift;
        }

        public Shift getExistingShift() {
            return existingShift;
        }
    }
}
