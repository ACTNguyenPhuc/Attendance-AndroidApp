package com.example.attendanceapplication.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShiftTeacherTest {

    @Test
    public void teacherDisplayName_prefersNewTeacherField() {
        Shift shift = new Shift();
        shift.setTeacherName("teacher_uid");
        shift.setTeacher("Nguyễn Văn An");

        assertEquals("Nguyễn Văn An", shift.getTeacherDisplayName());
    }

    @Test
    public void teacherDisplayName_fallsBackToLegacyTeacherName() {
        Shift shift = new Shift();
        shift.setTeacherName("Giảng viên cũ");

        assertEquals("Giảng viên cũ", shift.getTeacherDisplayName());
    }
}
