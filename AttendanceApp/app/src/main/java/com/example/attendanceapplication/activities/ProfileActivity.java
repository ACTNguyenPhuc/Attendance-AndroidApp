package com.example.attendanceapplication.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvCode, tvRole;
    private Button btnLogout;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_profile);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Hồ sơ");
            }
        }

        tvName   = findViewById(R.id.tv_name);
        tvEmail  = findViewById(R.id.tv_email);
        tvCode   = findViewById(R.id.tv_code);
        tvRole   = findViewById(R.id.tv_role);
        btnLogout = findViewById(R.id.btn_logout);

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc muốn đăng xuất?")
                    .setPositiveButton("Đăng xuất", (d, w) -> {
                        repo.signOut();
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show());
        }

        loadProfile();
    }

    private void loadProfile() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getUserProfile(uid,
                user -> runOnUiThread(() -> {
                    if (tvName  != null) tvName.setText(user.getName());
                    if (tvEmail != null) tvEmail.setText(user.getEmail());
                    if (tvCode  != null) tvCode.setText(user.getStudentCode());
                    if (tvRole  != null) tvRole.setText("teacher".equals(user.getRole()) ? "GIẢNG VIÊN" : "SINH VIÊN");
                }),
                e -> {}
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
