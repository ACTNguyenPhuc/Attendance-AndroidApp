package com.example.attendanceapplication;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.jakewharton.threetenabp.AndroidThreeTen;

public class AttendanceApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize ThreeTenABP for Java 8 date/time backport
        AndroidThreeTen.init(this);

        FirebaseApp.initializeApp(this);

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        boolean isDebuggable =
                (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        if (isDebuggable) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
            );
        }
    }
}
