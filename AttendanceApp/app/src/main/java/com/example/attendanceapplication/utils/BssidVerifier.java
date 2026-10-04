package com.example.attendanceapplication.utils;

import com.example.attendanceapplication.models.Attendance;

import java.util.Locale;

/**
 * Đối chiếu BSSID Wi-Fi của sinh viên với BSSID của phiên điểm danh.
 * Chỉ để phát hiện bất thường: kết quả CẢNH BÁO không bao giờ chặn điểm danh.
 */
public final class BssidVerifier {

    public static final String NOTE_SESSION_MISSING = "Phiên điểm danh chưa có BSSID Wi-Fi";
    public static final String NOTE_DEVICE_MISSING = "Không đọc được BSSID của thiết bị";
    public static final String NOTE_MISMATCH = "Wi-Fi của thiết bị khác Wi-Fi của lớp";

    /** Android trả về địa chỉ này khi không cho ứng dụng biết BSSID thật. */
    static final String HIDDEN_BSSID = "02:00:00:00:00:00";

    private BssidVerifier() {}

    public static final class Verdict {
        public final String status;   // Attendance.BSSID_VALID / BSSID_WARNING
        public final String note;     // null nếu hợp lệ

        Verdict(String status, String note) {
            this.status = status;
            this.note = note;
        }

        public boolean isWarning() { return Attendance.BSSID_WARNING.equals(status); }
    }

    /**
     * @param deviceNote lý do không đọc được BSSID của thiết bị (nếu có), ưu tiên
     *                   hơn thông báo chung khi {@code deviceBssid} rỗng.
     */
    public static Verdict evaluate(String sessionBssid, String deviceBssid, String deviceNote) {
        String expected = normalize(sessionBssid);
        String actual = normalize(deviceBssid);
        if (expected == null) {
            return new Verdict(Attendance.BSSID_WARNING, NOTE_SESSION_MISSING);
        }
        if (actual == null) {
            return new Verdict(Attendance.BSSID_WARNING,
                    deviceNote != null ? deviceNote : NOTE_DEVICE_MISSING);
        }
        if (!expected.equals(actual)) {
            return new Verdict(Attendance.BSSID_WARNING, NOTE_MISMATCH);
        }
        return new Verdict(Attendance.BSSID_VALID, null);
    }

    /**
     * Chuẩn hoá về chữ thường; trả null cho giá trị rỗng hoặc địa chỉ giả
     * 02:00:00:00:00:00 mà Android trả về khi ẩn BSSID.
     */
    public static String normalize(String bssid) {
        if (bssid == null) return null;
        String value = bssid.trim().toLowerCase(Locale.US);
        if (value.isEmpty() || HIDDEN_BSSID.equals(value)) return null;
        return value;
    }
}
