package com.example.attendanceapplication;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.jakewharton.threetenabp.AndroidThreeTen;

public class AttendanceApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidThreeTen.init(this); // Khởi tạo thư viện ngày giờ
        FirebaseApp.initializeApp(this); // Khởi tạo firebase
    }
}
