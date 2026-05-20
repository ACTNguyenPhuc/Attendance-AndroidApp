package com.example.attendanceapplication.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * LocationService wraps FusedLocationProviderClient into a simple
 * callback-based utility for one-shot and continuous GPS.
 */
public class LocationService {

    public interface LocationCallback2 {
        void onLocationReady(Location location);
        void onError(String message);
    }

    private final Context context;
    private final FusedLocationProviderClient fusedClient;
    private LocationCallback continuousCallback;

    public LocationService(Context context) {
        this.context = context;
        fusedClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /** One-shot high accuracy location request */
    public void getCurrentLocation(LocationCallback2 callback) {
        if (!hasLocationPermission()) {
            callback.onError("Chưa có quyền GPS");
            return;
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        callback.onLocationReady(location);
                    } else {
                        callback.onError("Không lấy được vị trí, hãy bật GPS");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Start continuous location updates (for teacher GPS anchor) */
    public void startContinuousUpdates(LocationCallback2 callback) {
        if (!hasLocationPermission()) {
            callback.onError("Chưa có quyền GPS");
            return;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(3000)
                .build();

        continuousCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result != null && !result.getLocations().isEmpty()) {
                    callback.onLocationReady(result.getLocations().get(0));
                }
            }
        };

        fusedClient.requestLocationUpdates(request, continuousCallback, Looper.getMainLooper());
    }

    /** Stop continuous updates */
    public void stopContinuousUpdates() {
        if (continuousCallback != null) {
            fusedClient.removeLocationUpdates(continuousCallback);
            continuousCallback = null;
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Format GPS coordinates for display.
     */
    public static String formatCoords(double lat, double lng) {
        return String.format("%.6f, %.6f", lat, lng);
    }
}
