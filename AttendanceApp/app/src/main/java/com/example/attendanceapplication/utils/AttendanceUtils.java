package com.example.attendanceapplication.utils;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class AttendanceUtils {

    /**
     * Haversine formula to calculate distance (meters) between two GPS coordinates.
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * Generate a QR code bitmap from content string.
     */
    public static Bitmap generateQRCode(String content, int size) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate a unique token for QR codes.
     */
    public static String generateToken() {
        return "QR_" + System.currentTimeMillis() + "_" +
                (int)(Math.random() * 100000);
    }

    /**
     * Generate a unique session ID.
     */
    public static String generateSessionId(String classId, String shiftId) {
        return "session_" + classId + "_" + shiftId + "_" + System.currentTimeMillis();
    }

    /**
     * Check if a distance is within allowed radius.
     */
    public static boolean isWithinRadius(double distance, double radius) {
        return distance <= radius;
    }

    /**
     * Format distance for display.
     */
    public static String formatDistance(double distanceMeters) {
        if (distanceMeters < 1000) {
            return String.format("%.0fm", distanceMeters);
        } else {
            return String.format("%.1fkm", distanceMeters / 1000);
        }
    }

    /**
     * Whole-day difference between a shift date ("yyyy-MM-dd") and today.
     * 0 = today, 1 = tomorrow, negative = in the past.
     * Returns Long.MAX_VALUE if the date can't be parsed (treated as far future).
     */
    public static long daysFromToday(String date) {
        try {
            org.threeten.bp.LocalDate shiftDate = org.threeten.bp.LocalDate.parse(date);
            return org.threeten.bp.temporal.ChronoUnit.DAYS.between(
                    org.threeten.bp.LocalDate.now(), shiftDate);
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Whether an "upcoming" shift should show the "Sắp diễn ra" badge:
     * only when the shift is today or within 1 day ahead.
     */
    public static boolean shouldShowUpcomingBadge(String date) {
        long days = daysFromToday(date);
        return days >= 0 && days <= 1;
    }

    /**
     * Get day-of-week integer from Calendar constant (Java Calendar uses 1=Sun, 2=Mon...).
     * Convert to Vietnamese convention: 2=Mon ... 8=Sun
     */
    public static int getDayOfWeekVN(int calendarDayOfWeek) {
        // Calendar: 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
        // VN: 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat, 8=Sun
        if (calendarDayOfWeek == 1) return 8; // Sunday
        return calendarDayOfWeek; // Mon-Sat stays same offset
    }
}
