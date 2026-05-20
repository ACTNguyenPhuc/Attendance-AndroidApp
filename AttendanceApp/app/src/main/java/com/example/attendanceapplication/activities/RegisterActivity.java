package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilCode, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etName, etCode, etEmail, etPassword, etConfirmPassword;
    private RadioGroup rgRole;
    private RadioButton rbStudent, rbTeacher;
    private Button btnRegister;
    private TextView tvLoginRedirect;
    private View loadingOverlay;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initViews();
        setupClickListeners();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initViews() {
        tilName            = findViewById(R.id.til_name);
        tilCode            = findViewById(R.id.til_code);
        tilEmail           = findViewById(R.id.til_email);
        tilPassword        = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        etName             = findViewById(R.id.et_name);
        etCode             = findViewById(R.id.et_code);
        etEmail            = findViewById(R.id.et_email);
        etPassword         = findViewById(R.id.et_password);
        etConfirmPassword  = findViewById(R.id.et_confirm_password);
        rgRole             = findViewById(R.id.rg_role);
        rbStudent          = findViewById(R.id.rb_student);
        rbTeacher          = findViewById(R.id.rb_teacher);
        btnRegister        = findViewById(R.id.btn_register);
        tvLoginRedirect    = findViewById(R.id.tv_login_redirect);
        loadingOverlay     = findViewById(R.id.loading_overlay);

        rbStudent.setChecked(true);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        tvLoginRedirect.setOnClickListener(v -> finish());
    }

    private boolean validateForm() {
        boolean valid = true;
        tilName.setError(null);
        tilCode.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        String name     = etName.getText().toString().trim();
        String code     = etCode.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirm  = etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) { tilName.setError("Vui lòng nhập họ và tên"); valid = false; }
        if (code.isEmpty()) { tilCode.setError("Vui lòng nhập mã số"); valid = false; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Địa chỉ email không hợp lệ"); valid = false;
        }
        if (password.length() < 6) { tilPassword.setError("Mật khẩu tối thiểu 6 ký tự"); valid = false; }
        if (!password.equals(confirm)) { tilConfirmPassword.setError("Mật khẩu xác nhận không khớp"); valid = false; }

        return valid;
    }

    private void attemptRegister() {
        if (!validateForm()) return;

        String name     = etName.getText().toString().trim();
        String code     = etCode.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String role     = rbTeacher.isChecked() ? User.ROLE_TEACHER : User.ROLE_STUDENT;

        User user = new User();
        user.setName(name);
        user.setStudentCode(code);
        user.setEmail(email);
        user.setRole(role);

        loadingOverlay.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        repo.register(email, password, user,
                uid -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Snackbar.make(btnRegister, "Đăng ký thành công! Hãy đăng nhập", Snackbar.LENGTH_LONG).show();
                    new android.os.Handler().postDelayed(() -> {
                        startActivity(new Intent(this, LoginActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                        finish();
                    }, 1500);
                },
                e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    Snackbar.make(btnRegister, "Đăng ký thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                }
        );
    }
}
