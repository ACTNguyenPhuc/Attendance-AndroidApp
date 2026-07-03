package com.example.attendanceapplication.utils;

import com.example.attendanceapplication.models.Shift;

import java.util.List;

/** Các quy tắc thuần Java dùng để phát hiện hai ca học chiếm cùng một phòng. */
public final class RoomConflictChecker {

    private RoomConflictChecker() {}

    public static Conflict findFirstConflict(List<Shift> newShifts, List<Shift> existingShifts) {
        if (newShifts == null || existingShifts == null) return null;

        for (Shift newShift : newShifts) {
            if (newShift == null) continue;
            for (Shift existingShift : existingShifts) {
                if (existingShift == null) continue;
                if (!sameValue(newShift.getDate(), existingShift.getDate())) continue;
                if (!sameRoom(newShift.getRoom(), existingShift.getRoom())) continue;
                if (timesOverlap(newShift.getStartAt(), newShift.getEndAt(),
                        existingShift.getStartAt(), existingShift.getEndAt())) {
                    return new Conflict(newShift, existingShift);
                }
            }
        }
        return null;
    }

    /** So sánh phòng không phân biệt hoa/thường và khoảng trắng thừa. */
    static boolean sameRoom(String first, String second) {
        String normalizedFirst = normalize(first);
        String normalizedSecond = normalize(second);
        return !normalizedFirst.isEmpty() && normalizedFirst.equalsIgnoreCase(normalizedSecond);
    }

    /**
     * Kiểm tra giao nhau theo khoảng nửa mở [start, end). Vì vậy hai ca nối tiếp,
     * ví dụ 09:00-10:00 và 10:00-11:00, không bị xem là xung đột.
     */
    public static boolean timesOverlap(String start1, String end1, String start2, String end2) {
        int s1 = parseMinutes(start1);
        int e1 = parseMinutes(end1);
        int s2 = parseMinutes(start2);
        int e2 = parseMinutes(end2);
        if (s1 < 0 || e1 <= s1 || s2 < 0 || e2 <= s2) return false;
        return s1 < e2 && s2 < e1;
    }

    private static boolean sameValue(String first, String second) {
        return first != null && first.equals(second);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parseMinutes(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) return -1;
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
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
