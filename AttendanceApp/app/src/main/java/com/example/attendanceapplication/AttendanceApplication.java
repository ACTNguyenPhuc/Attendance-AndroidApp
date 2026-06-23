package com.example.attendanceapplication;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.google.firebase.FirebaseApp;
import com.jakewharton.threetenabp.AndroidThreeTen;

public class AttendanceApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize ThreeTenABP for Java 8 date/time backport
        AndroidThreeTen.init(this);

        FirebaseApp.initializeApp(this);

        // App Check đã được TẮT ở client: enforcement đang tắt trên Firebase Console,
        // và việc cài provider (debug/Play Integrity) làm Auth/Firestore treo chờ token
        // trên thiết bị/APK chưa đăng ký. Khi nào cần bảo mật lại thì bật enforcement
        // trên Console + cài lại provider + đăng ký debug token / SHA-256.
    }
}
