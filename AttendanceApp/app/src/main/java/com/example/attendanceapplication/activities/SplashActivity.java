package com.example.attendanceapplication.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseUser;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Phải gọi trước super.onCreate: hiện logo splash cho tới khi xác định xong màn đích
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> !navigated);

        checkAuthState();
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
        navigated = true;
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
