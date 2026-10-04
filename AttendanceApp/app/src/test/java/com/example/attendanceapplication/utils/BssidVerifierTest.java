package com.example.attendanceapplication.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.attendanceapplication.models.Attendance;

import org.junit.Test;

public class BssidVerifierTest {

    @Test
    public void matchingBssidIsValidIgnoringCase() {
        BssidVerifier.Verdict v = BssidVerifier.evaluate(
                "aa:bb:cc:dd:ee:ff", " AA:BB:CC:DD:EE:FF ", null);
        assertEquals(Attendance.BSSID_VALID, v.status);
        assertFalse(v.isWarning());
        assertNull(v.note);
    }

    @Test
    public void differentBssidIsWarning() {
        BssidVerifier.Verdict v = BssidVerifier.evaluate(
                "aa:bb:cc:dd:ee:ff", "11:22:33:44:55:66", null);
        assertTrue(v.isWarning());
        assertEquals(BssidVerifier.NOTE_MISMATCH, v.note);
    }

    @Test
    public void missingDeviceBssidIsWarningWithDeviceReason() {
        BssidVerifier.Verdict v = BssidVerifier.evaluate(
                "aa:bb:cc:dd:ee:ff", null, "Thiết bị không kết nối Wi-Fi");
        assertTrue(v.isWarning());
        assertEquals("Thiết bị không kết nối Wi-Fi", v.note);
    }

    @Test
    public void hiddenDeviceBssidCountsAsMissing() {
        BssidVerifier.Verdict v = BssidVerifier.evaluate(
                "aa:bb:cc:dd:ee:ff", "02:00:00:00:00:00", null);
        assertTrue(v.isWarning());
        assertEquals(BssidVerifier.NOTE_DEVICE_MISSING, v.note);
    }

    @Test
    public void sessionWithoutBssidIsWarning() {
        BssidVerifier.Verdict v = BssidVerifier.evaluate(
                null, "aa:bb:cc:dd:ee:ff", null);
        assertTrue(v.isWarning());
        assertEquals(BssidVerifier.NOTE_SESSION_MISSING, v.note);
    }
}
