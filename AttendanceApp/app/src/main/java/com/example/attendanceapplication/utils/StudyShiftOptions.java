package com.example.attendanceapplication.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Danh sách ca học cố định dùng chung khi tạo lớp, tạo ca học bù và dời ca.
 * Firestore vẫn lưu startAt/endAt để tương thích với dữ liệu và luồng điểm danh hiện có.
 */
public final class StudyShiftOptions {

    public static final class Option {
        private final String label;
        private final String startAt;
        private final String endAt;

        private Option(String label, String startAt, String endAt) {
            this.label = label;
            this.startAt = startAt;
            this.endAt = endAt;
        }

        public String getLabel() {
            return label;
        }

        public String getStartAt() {
            return startAt;
        }

        public String getEndAt() {
            return endAt;
        }
    }

    private static final List<Option> OPTIONS = Collections.unmodifiableList(Arrays.asList(
            new Option("Ca 1: 07h00 - 09h25", "07:00", "09:25"),
            new Option("Ca 2: 09h35 - 12h00", "09:35", "12:00"),
            new Option("Ca 3: 13h30 - 15h55", "12:30", "14:55"),
            new Option("Ca 4: 16h05 - 18h30", "15:05", "17:30"),
            new Option("Ca 5: 18h00 - 21h25", "18:00", "21:25")
    ));

    private static final List<String> LABELS;

    static {
        List<String> labels = new ArrayList<>();
        for (Option option : OPTIONS) {
            labels.add(option.getLabel());
        }
        LABELS = Collections.unmodifiableList(labels);
    }

    private StudyShiftOptions() {
    }

    public static List<String> getLabels() {
        return LABELS;
    }

    public static Option get(int position) {
        return OPTIONS.get(position);
    }

    public static Option findByTimes(String startAt, String endAt) {
        for (Option option : OPTIONS) {
            if (option.getStartAt().equals(startAt) && option.getEndAt().equals(endAt)) {
                return option;
            }
        }
        return null;
    }

    public static boolean isValid(String startAt, String endAt) {
        return findByTimes(startAt, endAt) != null;
    }
}
