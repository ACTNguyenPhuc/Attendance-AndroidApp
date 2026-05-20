package com.example.attendanceapplication.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseUser;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2 seconds
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthState, SPLASH_DELAY);
    }

    private void checkAuthState() {
        FirebaseUser currentUser = repo.getCurrentUser();
        if (currentUser == null) {
            navigateTo(LoginActivity.class);
            return;
        }

        // User is logged in — fetch role
        repo.getUserProfile(currentUser.getUid(),
                user -> {
                    if (User.ROLE_TEACHER.equals(user.getRole())) {
                        navigateTo(TeacherMainActivity.class);
                    } else {
                        navigateTo(StudentMainActivity.class);
                    }
                },
                e -> navigateTo(LoginActivity.class)
        );
    }

    private void navigateTo(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
