package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private TextView tvForgotPassword;
    private ProgressBar progressBar;
    private View overlay;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupTextWatchers();
        setupClickListeners();
    }

    private void initViews() {
        tilEmail       = findViewById(R.id.til_email);
        tilPassword    = findViewById(R.id.til_password);
        etEmail        = findViewById(R.id.et_email);
        etPassword     = findViewById(R.id.et_password);
        btnLogin       = findViewById(R.id.btn_login);
        btnRegister    = findViewById(R.id.btn_register);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        progressBar    = findViewById(R.id.progress_bar);
        overlay        = findViewById(R.id.loading_overlay);

        btnLogin.setEnabled(false);
    }

    private void setupTextWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                validateForm();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
    }

    private void validateForm() {
        String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        btnLogin.setEnabled(!email.isEmpty() && password.length() >= 6);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
        tvForgotPassword.setOnClickListener(v ->
                Toast.makeText(this, "Vui lòng liên hệ quản trị viên", Toast.LENGTH_SHORT).show()
        );
    }

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Địa chỉ email không hợp lệ");
            return;
        }

        showLoading(true);

        repo.login(email, password,
                firebaseUser -> repo.getUserProfile(
                        firebaseUser.getUid(),
                        user -> {
                            showLoading(false);
                            redirectByRole(user);
                        },
                        e -> {
                            showLoading(false);
                            showError("Không thể tải thông tin người dùng");
                        }
                ),
                e -> {
                    showLoading(false);
                    if (e instanceof FirebaseAuthInvalidCredentialsException ||
                            e instanceof FirebaseAuthInvalidUserException) {
                        showError("Email hoặc mật khẩu không đúng");
                    } else {
                        showError("Đăng nhập thất bại: " + e.getMessage());
                    }
                }
        );
    }

    private void redirectByRole(User user) {
        Intent intent;
        if (User.ROLE_TEACHER.equals(user.getRole())) {
            intent = new Intent(this, TeacherMainActivity.class);
        } else {
            intent = new Intent(this, StudentMainActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        overlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
    }

    private void showError(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }
}
