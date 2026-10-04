package com.example.attendanceapplication.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

/** Đọc BSSID của Wi-Fi mà thiết bị đang kết nối. */
public final class WifiUtils {

    private WifiUtils() {}

    public static final class WifiReading {
        public final String bssid;   // null nếu không đọc được
        public final String note;    // lý do khi bssid == null

        WifiReading(String bssid, String note) {
            this.bssid = bssid;
            this.note = note;
        }
    }

    /**
     * Android chỉ trả BSSID thật khi có quyền vị trí chính xác và dịch vụ vị trí
     * đang bật; mọi trường hợp khác trả về {@code bssid = null} kèm lý do.
     */
    @SuppressWarnings("deprecation")
    public static WifiReading readCurrentBssid(Context context) {
        Context app = context.getApplicationContext();
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return new WifiReading(null, "Chưa cấp quyền vị trí để đọc Wi-Fi");
        }
        if (!isOnWifi(app)) {
            return new WifiReading(null, "Thiết bị không kết nối Wi-Fi");
        }
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null && !LocationManagerCompat.isLocationEnabled(lm)) {
            return new WifiReading(null, "Dịch vụ vị trí đang tắt nên không đọc được Wi-Fi");
        }
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wm == null ? null : wm.getConnectionInfo();
        String bssid = BssidVerifier.normalize(info == null ? null : info.getBSSID());
        if (bssid == null) {
            return new WifiReading(null, BssidVerifier.NOTE_DEVICE_MISSING);
        }
        return new WifiReading(bssid, null);
    }

    private static boolean isOnWifi(Context app) {
        ConnectivityManager cm =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
