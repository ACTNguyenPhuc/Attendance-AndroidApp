package com.example.attendanceapplication.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StudyShiftOptionsTest {

    @Test
    public void exposesFiveFixedStudyShifts() {
        assertEquals(5, StudyShiftOptions.getLabels().size());
        assertEquals("Ca 1: 07h00 - 09h25", StudyShiftOptions.getLabels().get(0));
        assertEquals("Ca 5 (Tối): 19h00 - 21h25", StudyShiftOptions.getLabels().get(4));
    }

    @Test
    public void findsOptionByStoredFirestoreTimes() {
        StudyShiftOptions.Option option = StudyShiftOptions.findByTimes("13:30", "15:55");

        assertNotNull(option);
        assertEquals("Ca 3: 13h30 - 15h55", option.getLabel());
    }

    @Test
    public void rejectsTimesOutsideFixedOptions() {
        assertTrue(StudyShiftOptions.isValid("16:05", "18:30"));
        assertFalse(StudyShiftOptions.isValid("16:00", "18:30"));
        assertNull(StudyShiftOptions.findByTimes(null, null));
    }
}
